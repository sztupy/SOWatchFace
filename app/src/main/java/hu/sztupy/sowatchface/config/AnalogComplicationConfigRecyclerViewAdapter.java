package hu.sztupy.sowatchface.config;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.support.wearable.complications.ProviderInfoRetriever.OnProviderInfoReceivedCallback;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import hu.sztupy.sowatchface.R;
import hu.sztupy.sowatchface.model.AnalogComplicationConfigData.ConfigItemType;
import hu.sztupy.sowatchface.model.AnalogComplicationConfigData.PreviewAndComplicationsConfigItem;
import hu.sztupy.sowatchface.model.AnalogComplicationConfigData.SwitchConfigItem;
import hu.sztupy.sowatchface.model.AnalogComplicationConfigData.InputConfigItem;
import hu.sztupy.sowatchface.model.AnalogComplicationConfigData.ValueDisplayConfigItem;
import hu.sztupy.sowatchface.watchface.SOWatchFace;

import java.util.ArrayList;
import java.util.concurrent.Executors;

import static hu.sztupy.sowatchface.config.SiteSelectorActivity.EXTRA_SHARED_PREF;

/**
 * Displays different layouts for configuring watch face's complications and appearance settings
 * (highlight color [second arm], background color, unread notifications, etc.).
 *
 * <p>All appearance settings are saved via {@link SharedPreferences}.
 *
 * <p>Layouts provided by this adapter are split into 5 main view types.
 *
 * <p>A watch face preview including complications. Allows user to tap on the complications to
 * change the complication data and see a live preview of the watch face.
 *
 * <p>Simple arrow to indicate there are more options below the fold.
 *
 * <p>Color configuration options for both highlight (seconds hand) and background color.
 *
 * <p>Toggle for unread notifications.
 *
 * <p>Background image complication configuration for changing background image of watch face.
 */
public class AnalogComplicationConfigRecyclerViewAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "CompConfigAdapter";

    public static final int JON_SKEET_ID = 22656;
    public static final int JON_SKEET_SE_ID = 11683;
    public static final String STACKOVERFLOW_NAME = "stackoverflow";

    /**
     * Used by associated watch face ({@link SOWatchFace}) to let this
     * adapter know which complication locations are supported, their ids, and supported
     * complication data types.
     */
    public enum ComplicationLocation {
        BACKGROUND,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    public static final int TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG = 0;
    public static final int TYPE_SWITCH_CONFIG = 1;
    public static final int TYPE_INPUT_CONFIG = 2;
    public static final int TYPE_VALUE_CONFIG = 3;

    // ComponentName associated with watch face service (service that renders watch face). Used
    // to retrieve complication information.
    private ComponentName mWatchFaceComponentName;

    private ArrayList<ConfigItemType> mSettingsDataSet;
    private ArrayList<ValueDisplayViewHolder> mValueDisplayViewHolders;

    private Context mContext;

    SharedPreferences mSharedPref;

    // Selected complication id by user.
    private int mSelectedComplicationId;

    private int mLeftComplicationId;
    private int mRightComplicationId;

    // Required to retrieve complication data from watch face for preview.
    private ProviderInfoRetriever mProviderInfoRetriever;

    // Maintains reference view holder to dynamically update watch face preview. Used instead of
    // notifyItemChanged(int position) to avoid flicker and re-inflating the view.
    private PreviewAndComplicationsViewHolder mPreviewAndComplicationsViewHolder;

    public AnalogComplicationConfigRecyclerViewAdapter(
            Context context,
            Class watchFaceServiceClass,
            ArrayList<ConfigItemType> settingsDataSet) {

        mContext = context;
        mWatchFaceComponentName = new ComponentName(mContext, watchFaceServiceClass);
        mSettingsDataSet = settingsDataSet;

        // Default value is invalid (only changed when user taps to change complication).
        mSelectedComplicationId = -1;

        mLeftComplicationId =
                SOWatchFace.getComplicationId(ComplicationLocation.LEFT);
        mRightComplicationId =
                SOWatchFace.getComplicationId(ComplicationLocation.RIGHT);

        mSharedPref =
                context.getSharedPreferences(
                        context.getString(R.string.analog_complication_preference_file_key),
                        Context.MODE_PRIVATE);

        mValueDisplayViewHolders = new ArrayList<>();

        // Initialization of code to retrieve active complication data for the watch face.
        mProviderInfoRetriever =
                new ProviderInfoRetriever(mContext, Executors.newCachedThreadPool());
        mProviderInfoRetriever.init();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder(): viewType: " + viewType);

        RecyclerView.ViewHolder viewHolder = null;

        switch (viewType) {
            case TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG:
                // Need direct reference to watch face preview view holder to update watch face
                // preview based on selections from the user.
                mPreviewAndComplicationsViewHolder =
                        new PreviewAndComplicationsViewHolder(
                                LayoutInflater.from(parent.getContext())
                                        .inflate(
                                                R.layout.config_list_preview_and_complications_item,
                                                parent,
                                                false));
                viewHolder = mPreviewAndComplicationsViewHolder;
                break;

            case TYPE_SWITCH_CONFIG:
                viewHolder =
                        new SwitchViewHolder(
                                LayoutInflater.from(parent.getContext())
                                        .inflate(
                                                R.layout.config_switch_item,
                                                parent,
                                                false));
                break;

            case TYPE_INPUT_CONFIG:
                viewHolder =
                        new InputViewHolder(
                                LayoutInflater.from(parent.getContext())
                                        .inflate(
                                                R.layout.config_input,
                                                parent,
                                                false));
                break;
            case TYPE_VALUE_CONFIG:
                viewHolder =
                        new ValueDisplayViewHolder(
                                LayoutInflater.from(parent.getContext())
                                        .inflate(
                                                R.layout.config_value_display,
                                                parent,
                                                false));
                break;
        }

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        Log.d(TAG, "Element " + position + " set.");

        // Pulls all data required for creating the UX for the specific setting option.
        ConfigItemType configItemType = mSettingsDataSet.get(position);

        switch (viewHolder.getItemViewType()) {
            case TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG:
                PreviewAndComplicationsViewHolder previewAndComplicationsViewHolder =
                        (PreviewAndComplicationsViewHolder) viewHolder;

                PreviewAndComplicationsConfigItem previewAndComplicationsConfigItem =
                        (PreviewAndComplicationsConfigItem) configItemType;

                int defaultComplicationResourceId =
                        previewAndComplicationsConfigItem.getDefaultComplicationResourceId();
                previewAndComplicationsViewHolder.setDefaultComplicationDrawable(
                        defaultComplicationResourceId);

                previewAndComplicationsViewHolder.initializesColorsAndComplications();
                break;

            case TYPE_SWITCH_CONFIG:
                SwitchViewHolder unreadViewHolder =
                        (SwitchViewHolder) viewHolder;

                SwitchConfigItem unreadConfigItem =
                        (SwitchConfigItem) configItemType;

                int unreadEnabledIconResourceId = unreadConfigItem.getIconEnabledResourceId();
                int unreadDisabledIconResourceId = unreadConfigItem.getIconDisabledResourceId();

                String unreadName = unreadConfigItem.getName();
                int unreadSharedPrefId = unreadConfigItem.getSharedPrefId();

                unreadViewHolder.setIcons(
                        unreadEnabledIconResourceId, unreadDisabledIconResourceId);
                unreadViewHolder.setName(unreadName);
                unreadViewHolder.setSharedPrefId(unreadSharedPrefId);
                break;

            case TYPE_INPUT_CONFIG:
                InputViewHolder inputViewHolder =
                        (InputViewHolder) viewHolder;

                InputConfigItem inputConfigItem =
                        (InputConfigItem) configItemType;

                int iconId = inputConfigItem.getIconResourceId();
                int inputSharedPrefId = inputConfigItem.getSharedPrefId();
                String inputName = inputConfigItem.getName();

                inputViewHolder.setName(inputName);
                inputViewHolder.setIcon(iconId);
                inputViewHolder.setSharedPrefId(inputSharedPrefId);
                break;
            case TYPE_VALUE_CONFIG:
                ValueDisplayViewHolder valueDisplayViewHolder =
                        (ValueDisplayViewHolder) viewHolder;

                ValueDisplayConfigItem valueDisplayConfigItem =
                        (ValueDisplayConfigItem) configItemType;

                int sbIconId = valueDisplayConfigItem.getIconResourceId();
                int selectBoxSharedPrefId = valueDisplayConfigItem.getSharedPrefId();
                String selectBoxName = valueDisplayConfigItem.getName();
                Class<SiteSelectorActivity> launchActivity = valueDisplayConfigItem.getLaunchActivity();

                valueDisplayViewHolder.setName(selectBoxName);
                valueDisplayViewHolder.setIcon(sbIconId);
                valueDisplayViewHolder.setSharedPrefId(selectBoxSharedPrefId);
                valueDisplayViewHolder.setLaunchActivity(launchActivity);

                mValueDisplayViewHolders.add(valueDisplayViewHolder);
                break;
        }
    }

    @Override
    public int getItemViewType(int position) {
        ConfigItemType configItemType = mSettingsDataSet.get(position);
        return configItemType.getConfigType();
    }

    @Override
    public int getItemCount() {
        return mSettingsDataSet.size();
    }

    /** Updates the selected complication id saved earlier with the new information. */
    public void updateSelectedComplication(ComplicationProviderInfo complicationProviderInfo) {

        Log.d(TAG, "updateSelectedComplication: " + mPreviewAndComplicationsViewHolder);

        // Checks if view is inflated and complication id is valid.
        if (mPreviewAndComplicationsViewHolder != null && mSelectedComplicationId >= 0) {
            mPreviewAndComplicationsViewHolder.updateComplicationViews(
                    mSelectedComplicationId, complicationProviderInfo);
        }
    }

    public void updateSiteName() {
        for (ValueDisplayViewHolder viewHolder : mValueDisplayViewHolders) {
            viewHolder.updatePreference();
        }
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        // Required to release retriever for active complication data on detach.
        mProviderInfoRetriever.release();
    }

    /**
     * Displays watch face preview along with complication locations. Allows user to tap on the
     * complication they want to change and preview updates dynamically.
     */
    public class PreviewAndComplicationsViewHolder extends RecyclerView.ViewHolder
            implements OnClickListener {

        private View mComplicationsPreviewView;

        private ImageView mLeftComplicationBackground;
        private ImageView mRightComplicationBackground;

        private ImageButton mLeftComplication;
        private ImageButton mRightComplication;

        private Drawable mDefaultComplicationDrawable;

        public PreviewAndComplicationsViewHolder(final View view) {
            super(view);

            mComplicationsPreviewView = view.findViewById(R.id.complications_preview);

            // Sets up left complication preview.
            mLeftComplicationBackground =
                    (ImageView) view.findViewById(R.id.left_complication_background);
            mLeftComplication = (ImageButton) view.findViewById(R.id.left_complication);
            mLeftComplication.setOnClickListener(this);

            // Sets up right complication preview.
            mRightComplicationBackground =
                    (ImageView) view.findViewById(R.id.right_complication_background);
            mRightComplication = (ImageButton) view.findViewById(R.id.right_complication);
            mRightComplication.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (view.equals(mLeftComplication)) {
                Log.d(TAG, "Left Complication click()");

                Activity currentActivity = (Activity) view.getContext();
                launchComplicationHelperActivity(currentActivity, ComplicationLocation.LEFT);

            } else if (view.equals(mRightComplication)) {
                Log.d(TAG, "Right Complication click()");

                Activity currentActivity = (Activity) view.getContext();
                launchComplicationHelperActivity(currentActivity, ComplicationLocation.RIGHT);
            }
        }

        // Verifies the watch face supports the complication location, then launches the helper
        // class, so user can choose their complication data provider.
        private void launchComplicationHelperActivity(
                Activity currentActivity, ComplicationLocation complicationLocation) {

            mSelectedComplicationId =
                    SOWatchFace.getComplicationId(complicationLocation);

            if (mSelectedComplicationId >= 0) {

                int[] supportedTypes =
                        SOWatchFace.getSupportedComplicationTypes(
                                complicationLocation);

                ComponentName watchFace =
                        new ComponentName(
                                currentActivity, SOWatchFace.class);

                currentActivity.startActivityForResult(
                        ComplicationHelperActivity.createProviderChooserHelperIntent(
                                currentActivity,
                                watchFace,
                                mSelectedComplicationId,
                                supportedTypes),
                        AnalogComplicationConfigActivity.COMPLICATION_CONFIG_REQUEST_CODE);

            } else {
                Log.d(TAG, "Complication not supported by watch face.");
            }
        }

        public void setDefaultComplicationDrawable(int resourceId) {
            Context context = mComplicationsPreviewView.getContext();
            mDefaultComplicationDrawable = context.getDrawable(resourceId);

            mLeftComplication.setImageDrawable(mDefaultComplicationDrawable);
            mLeftComplicationBackground.setVisibility(View.INVISIBLE);

            mRightComplication.setImageDrawable(mDefaultComplicationDrawable);
            mRightComplicationBackground.setVisibility(View.INVISIBLE);
        }

        public void updateComplicationViews(
                int watchFaceComplicationId, ComplicationProviderInfo complicationProviderInfo) {
            Log.d(TAG, "updateComplicationViews(): id: " + watchFaceComplicationId);
            Log.d(TAG, "\tinfo: " + complicationProviderInfo);

            if (watchFaceComplicationId == mLeftComplicationId) {
                updateComplicationView(complicationProviderInfo, mLeftComplication,
                    mLeftComplicationBackground);

            } else if (watchFaceComplicationId == mRightComplicationId) {
                updateComplicationView(complicationProviderInfo, mRightComplication,
                    mRightComplicationBackground);
            }
        }

        private void updateComplicationView(ComplicationProviderInfo complicationProviderInfo,
            ImageButton button, ImageView background) {
            if (complicationProviderInfo != null) {
                button.setImageIcon(complicationProviderInfo.providerIcon);
                button.setContentDescription(
                    mContext.getString(R.string.edit_complication,
                        complicationProviderInfo.appName + " " +
                            complicationProviderInfo.providerName));
                background.setVisibility(View.VISIBLE);
            } else {
                button.setImageDrawable(mDefaultComplicationDrawable);
                button.setContentDescription(mContext.getString(R.string.add_complication));
                background.setVisibility(View.INVISIBLE);
            }
        }

        public void initializesColorsAndComplications() {
            final int[] complicationIds = SOWatchFace.getComplicationIds();

            mProviderInfoRetriever.retrieveProviderInfo(
                    new OnProviderInfoReceivedCallback() {
                        @Override
                        public void onProviderInfoReceived(
                                int watchFaceComplicationId,
                                @Nullable ComplicationProviderInfo complicationProviderInfo) {

                            Log.d(TAG, "onProviderInfoReceived: " + complicationProviderInfo);

                            updateComplicationViews(
                                    watchFaceComplicationId, complicationProviderInfo);
                        }
                    },
                    mWatchFaceComponentName,
                    complicationIds);
        }
    }

    /**
     * Displays switch with icon the user can toggle on and off.
     */
    public class SwitchViewHolder extends RecyclerView.ViewHolder
            implements OnClickListener {

        private Switch mSwitch;
        private View mView;

        private int mEnabledIconResourceId;
        private int mDisabledIconResourceId;

        private int mSharedPrefResourceId;

        public SwitchViewHolder(View view) {
            super(view);
            mView = view;
            mSwitch = (Switch) mView.findViewById(R.id.switch_item);
            mView.setOnClickListener(this);
        }

        public void setName(String name) {
            mSwitch.setText(name);
        }

        public void setIcons(int enabledIconResourceId, int disabledIconResourceId) {

            mEnabledIconResourceId = enabledIconResourceId;
            mDisabledIconResourceId = disabledIconResourceId;

            Context context = mSwitch.getContext();

            // Set default to enabled.
            mSwitch.setCompoundDrawablesWithIntrinsicBounds(
                    context.getDrawable(mEnabledIconResourceId), null, null, null);
        }

        public void setSharedPrefId(int sharedPrefId) {
            mSharedPrefResourceId = sharedPrefId;

            if (mSwitch != null) {

                Context context = mSwitch.getContext();
                String sharedPreferenceString = context.getString(mSharedPrefResourceId);
                Boolean currentState = mSharedPref.getBoolean(sharedPreferenceString, true);

                updateIcon(context, currentState);
            }
        }

        private void updateIcon(Context context, Boolean currentState) {
            int currentIconResourceId;

            if (currentState) {
                currentIconResourceId = mEnabledIconResourceId;
            } else {
                currentIconResourceId = mDisabledIconResourceId;
            }

            mSwitch.setChecked(currentState);
            mSwitch.setCompoundDrawablesWithIntrinsicBounds(
                    context.getDrawable(currentIconResourceId), null, null, null);
        }

        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();
            Log.d(TAG, "Complication onClick() position: " + position);

            Context context = view.getContext();
            String sharedPreferenceString = context.getString(mSharedPrefResourceId);

            // Since user clicked on a switch, new state should be opposite of current state.
            Boolean newState = !mSharedPref.getBoolean(sharedPreferenceString, true);

            SharedPreferences.Editor editor = mSharedPref.edit();
            editor.putBoolean(sharedPreferenceString, newState);
            editor.apply();

            updateIcon(context, newState);
        }
    }

    /**
     * Displays switch with icon the user can toggle on and off.
     */
    public class InputViewHolder extends RecyclerView.ViewHolder implements TextWatcher, OnClickListener {

        private EditText mInput;
        private TextView mLabel;
        private View mView;

        private int mIconResourceId;

        private int mSharedPrefResourceId;

        public InputViewHolder(View view) {
            super(view);
            mView = view;
            mInput = (EditText) mView.findViewById(R.id.input_item_edit);
            mLabel = (TextView) mView.findViewById(R.id.input_item_text);

            mInput.addTextChangedListener(this);
            mLabel.setOnClickListener(this);
        }

        public void setName(String name) {
            mLabel.setText(name);
        }

        public void setIcon(int iconResourceId) {

            mIconResourceId = iconResourceId;

            Context context = mLabel.getContext();

            // Set default to enabled.
            mLabel.setCompoundDrawablesWithIntrinsicBounds(
                    context.getDrawable(mIconResourceId), null, null, null);
        }

        public void setSharedPrefId(int sharedPrefId) {
            mSharedPrefResourceId = sharedPrefId;

            if (mInput != null) {

                Context context = mInput.getContext();
                String sharedPreferenceString = context.getString(mSharedPrefResourceId);
                int currentValue = mSharedPref.getInt(sharedPreferenceString, JON_SKEET_SE_ID);

                mInput.setText(currentValue + "");
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            if (mInput != null) {
                Context context = mInput.getContext();
                String sharedPreferenceString = context.getString(mSharedPrefResourceId);

                int newValue;
                try {
                    newValue = Integer.parseInt(s.toString());
                } catch (NumberFormatException e) {
                    newValue = JON_SKEET_SE_ID;
                }

                SharedPreferences.Editor editor = mSharedPref.edit();
                editor.putInt(sharedPreferenceString, newValue);
                editor.apply();
            }
        }

        @Override
        public void onClick(View v) {
            if (mInput!= null) {
                mInput.requestFocus();
            }
        }
    }


    /**
     * Displays switch with icon the user can toggle on and off.
     */
    public class ValueDisplayViewHolder extends RecyclerView.ViewHolder implements OnClickListener {

        private TextView mValue;
        private TextView mLabel;
        private View mView;

        private int mIconResourceId;

        private int mSharedPrefResourceId;

        private Class<SiteSelectorActivity> mLaunchActivity;


        public ValueDisplayViewHolder(View view) {
            super(view);
            mView = view;

            mValue = (TextView) mView.findViewById(R.id.select_item_value);
            mLabel = (TextView) mView.findViewById(R.id.select_item_text);

            mView.setOnClickListener(this);
            mValue.setOnClickListener(this);
            mLabel.setOnClickListener(this);
        }

        public void setLaunchActivity(Class<SiteSelectorActivity> activity) {
            mLaunchActivity = activity;
        }

        public void setName(String name) {
            mLabel.setText(name);
        }

        public void setIcon(int iconResourceId) {
            mIconResourceId = iconResourceId;

            Context context = mLabel.getContext();

            // Set default to enabled.
            mLabel.setCompoundDrawablesWithIntrinsicBounds(
                    context.getDrawable(mIconResourceId), null, null, null);
        }


        public void setSharedPrefId(int sharedPrefId) {
            mSharedPrefResourceId = sharedPrefId;

            updatePreference();
        }

        public void updatePreference() {
            if (mValue != null) {

                Context context = mValue.getContext();
                String sharedPreferenceString = context.getString(mSharedPrefResourceId);

                Object currentValue = mSharedPref.getAll().get(sharedPreferenceString);
                if (currentValue == null) {
                    currentValue = "";
                }

                mValue.setText(currentValue.toString());
            }
        }

        @Override
        public void onClick(View v) {
            if (mLaunchActivity != null) {
                Intent launchIntent = new Intent(mView.getContext(), mLaunchActivity);

                // Pass shared preference name to save color value to.
                launchIntent.putExtra(EXTRA_SHARED_PREF, mSharedPrefResourceId);

                Activity activity = (Activity) mView.getContext();
                activity.startActivityForResult(
                        launchIntent,
                        AnalogComplicationConfigActivity.SITE_SETTINGS_CONFIG_REQUEST_CODE);
            }
        }
    }

}
