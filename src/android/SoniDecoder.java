package cordova.plugin.decoder;

import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v7.app.AlertDialog;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PermissionHelper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import cordova.plugin.decoder.SoniTalkDecoder;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;

/**
 * This class echoes a string called from JavaScript.
 */
public class SoniDecoder extends CordovaPlugin implements SoniTalkDecoder.MessageListener, SoniTalkPermissionsResultReceiver.Receiver {

    private static final String PERMISSION_SONITALK_L0 = "at.ac.fhstp.permission_all_ultrasonic_communication";

    private static final String TAG = "cordova-plugin-decoder";

    private SoniTalkContext soniTalkContext;

    private SoniTalkDecoder soniTalkDecoder;
    private SoniTalkPermissionsResultReceiver soniTalkPermissionsResultReceiver;

    private int samplingRate = 44100;
    public static final int ON_RECEIVING_REQUEST_CODE = 2002;

    PluginResult result = null;
    private CallbackContext callbackContext = null;
    private String message;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 42;
    private String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO};

    private SharedPreferences sp;

    Context context;

    @Override
    public void pluginInitialize() {
        soniTalkPermissionsResultReceiver = new SoniTalkPermissionsResultReceiver(new Handler());
        soniTalkPermissionsResultReceiver.setReceiver(this);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        context = cordova.getActivity().getApplicationContext();

        if (action.equals("decode")) {
            this.message = args.getString(0);
            this.callbackContext = callbackContext;
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    onDecodeStart(message, callbackContext);
                }
            });

            /*PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);*/
            return true;
        }

        if (action.equals("check_microphone_permission")) {
            this.callbackContext = callbackContext;
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    checkMicrophonePermission();
                }
            });
            return true;
        }

        if (action.equals("check_special_permission")) {
            this.callbackContext = callbackContext;
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    checkSpecialPermission();
                }
            });
            return true;
        }
        return false;
    }

    private boolean checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            sendData("false");
            return false;
        } else {
            sendData("true");
            return true;
        }
    }

    private boolean checkSpecialPermission() {
        if (hasPermissions(context, PERMISSION_SONITALK_L0)) {
            sendData("true");
            return true;
        } else {
            sendData("false");
            return false;
        }
    }

    private void onDecodeStart(String message, CallbackContext callbackContext) {
        if (!hasPermissions(context, PERMISSIONS)) {
            requestAudioPermission();
        } else {
            decode(message, callbackContext);
        }
    }

    private void decode(String message, CallbackContext callbackContext) {

        /*if (message != null && message.length() > 0) {
            callbackContext.success(message);
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }*/

        int bitperiod = 100;
        int pauseperiod = 0;
        int f0 = 18000;
        int nFrequencies = 16;
        int frequencySpace = 100;
        int nMaxBytes = 30; //18*2-2=34

        try {
            // Note: here for debugging purpose we allow to change almost all the settings of the protocol.
            int nMessageBlocks = calculateNumberOfMessageBlocks(nFrequencies, nMaxBytes);// Default is 10 (transmitting 20 bytes with 16 frequencies)
            SoniTalkConfig config = new SoniTalkConfig(f0, bitperiod, pauseperiod, nMessageBlocks, nFrequencies, frequencySpace);
//            SoniTalkConfig config = getDefaultSettings();

            // Testing usage of a config file placed in the final-app asset folder.
            // SoniTalkConfig config = ConfigFactory.loadFromJson("default_config.json", this.getApplicationContext());


            if (soniTalkContext == null) {
                soniTalkContext = SoniTalkContext.getInstance(context, soniTalkPermissionsResultReceiver);
            }

            soniTalkDecoder = new SoniTalkDecoder(soniTalkContext, samplingRate, config); //, stepFactor, frequencyOffsetForSpectrogram, silentMode);
            soniTalkDecoder.addMessageListener(this); // MainActivity will be notified of messages received (calls onMessageReceived)
            //soniTalkDecoder.addSpectrumListener(this); // Can be used to receive the spectrum when a message is decoded.

            // Should not throw the DecoderStateException as we just initialized the Decoder
            soniTalkDecoder.receiveBackground(300000, ON_RECEIVING_REQUEST_CODE);

        } catch (DecoderStateException e) {
            Log.e(TAG, e.getMessage());
        }
    }


    public static int calculateNumberOfMessageBlocks(int nFrequencies, int nMaxBytes) {
        return (int) Math.ceil((nMaxBytes + 2) / (double) (nFrequencies / 8));
    }

    @Override
    public void onMessageReceived(SoniTalkMessage receivedMessage) {
        if (receivedMessage.isCrcCorrect()) {
            final String decodedText = DecoderUtils.byteToUTF8(receivedMessage.getMessage());
//            Log.e(TAG, decodedText);

            String myConvertedText = decodedText
                    .replaceAll("#h:", "http://")
                    .replaceAll("#hs:", "https://")
                    .replaceAll("\\\"", "\"")
                    .replaceAll("\\\\n", "\n")
                    .replaceAll("\\n", "\n")
                    .replaceAll("#"," ");
            sendData(myConvertedText);

//            callbackContext.success(decodedText);
        }
    }

    @Override
    public void onDecoderError(String errorMessage) {

    }

    @Override
    public void onSoniTalkPermissionResult(int resultCode, Bundle resultData) {
        int actionCode = 0;
        String resource_text;
        if (resultData != null) {
            resource_text = cordova.getActivity().getString(cordova.getActivity().getResources().getIdentifier("bundleRequestCode_key", "string", cordova.getActivity().getPackageName()));

            actionCode = resultData.getInt(resource_text);
        }
        if (resultCode == SoniTalkContext.ON_PERMISSION_LEVEL_DECLINED || resultCode == SoniTalkContext.ON_REQUEST_DENIED) {//Log.d(TAG, "ON_REQUEST_DENIED");


            // Checks the requestCode to adapt the UI depending on the action type (receiving or sending)
            //Log.d(TAG, "onSoniTalkPermissionResult ON_PERMISSION_LEVEL_DECLINED");
            //Log.d(TAG, String.valueOf(resultData.getInt(getString(R.string.bundleRequestCode_key), 0)));

            if (actionCode == ON_RECEIVING_REQUEST_CODE) {

                result = new PluginResult(PluginResult.Status.OK, "Data-over-sound permission required to receive messages");
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
//                this.callbackContext.success("Data-over-sound permission required to receive messages");
                //onButtonStopListening();

                // Set buttons in the state NOT RECEIVING
            }
        } else if (resultCode == SoniTalkContext.ON_REQUEST_GRANTED) {//Log.d(TAG, "ON_REQUEST_GRANTED");
            //Log.d(TAG, String.valueOf(resultData.getInt(getString(R.string.bundleRequestCode_key), 0)));

            if (actionCode == ON_RECEIVING_REQUEST_CODE) {// Set buttons in the state RECEIVING
            }

            //showRequestPermissionExplanation(R.string.on_receiving_listening_permission_required);
        } else if (resultCode == SoniTalkContext.ON_SHOULD_SHOW_RATIONALE_FOR_ALLOW_ALWAYS) {/*if (currentToast != null) {
                    currentToast.cancel();
                }
                currentToast = Toast.makeText(MainActivity.this, "Choosing Allow always requires you to accept the Android permission", Toast.LENGTH_LONG);
                currentToast.show();*/
        } else if (resultCode == SoniTalkContext.ON_REQUEST_L0_DENIED) {//Log.d(TAG, "ON_REQUEST_L0_DENIED");
            if (actionCode == ON_RECEIVING_REQUEST_CODE) {
                showRequestPermissionExplanation(cordova.getActivity().getResources().getIdentifier("on_receiving_listening_permission_required", "string", cordova.getActivity().getPackageName()));

            }
        } else {
            Log.w(TAG, "onSoniTalkPermissionResult unknown resultCode: " + resultCode);
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        super.onRequestPermissionResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) {
            //we will show an explanation next time the user click on start
            String resource_text;
            showRequestPermissionExplanation(cordova.getActivity().getResources().getIdentifier("permissionRequestExplanation", "string", cordova.getActivity().getPackageName()));
        }
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                decode(this.message, this.callbackContext);

            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {

                showRequestPermissionExplanation(cordova.getActivity().getResources().getIdentifier("permissionRequestExplanation", "string", cordova.getActivity().getPackageName()));
            }
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void showRequestPermissionExplanation(int messageId) {
        String pos_text = cordova.getActivity().getString(cordova.getActivity().getResources().getIdentifier("permission_request_explanation_positive", "string", cordova.getActivity().getPackageName()));
        String neg_text = cordova.getActivity().getString(cordova.getActivity().getResources().getIdentifier("permission_request_explanation_negative", "string", cordova.getActivity().getPackageName()));

        AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getActivity());
        builder.setMessage(messageId);
        builder.setPositiveButton(pos_text, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setAction(ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", cordova.getActivity().getPackageName(), null);
                        intent.setData(uri);
                        cordova.getActivity().startActivity(intent);
                    }
                }
        );
        builder.setNegativeButton(neg_text, null);
        builder.show();
    }

    public void requestAudioPermission() {
        Log.i(TAG, "Audio permission has NOT been granted. Requesting permission.");
        // If an explanation is needed
        if (ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(),
                Manifest.permission.RECORD_AUDIO)) {
            Log.i(TAG, "Displaying audio permission rationale to provide additional context.");

            ActivityCompat.requestPermissions(cordova.getActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
/*            Snackbar.make(rootViewGroup, R.string.permissionRequestExplanation,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(cordova.getActivity(),
                                    new String[]{Manifest.permission.RECORD_AUDIO},
                                    REQUEST_RECORD_AUDIO_PERMISSION);
                        }
                    })
                    .show();*/
        } else {
            // First time, no explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(cordova.getActivity(), PERMISSIONS, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    private void resetSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        int nMessageBlocks = calculateNumberOfMessageBlocks(Integer.parseInt(ConfigConstants.SETTING_NUMBER_OF_FREQUENCIES_DEFAULT), Integer.parseInt(ConfigConstants.SETTING_NUMBER_OF_BYTES_DEFAULT));// Default is 10 (transmitting 20 bytes with 16 frequencies)

        editor.putString(ConfigConstants.FREQUENCY_ZERO, ConfigConstants.SETTING_FREQUENCY_ZERO_DEFAULT);
        editor.putString(ConfigConstants.BIT_PERIOD, ConfigConstants.SETTING_BIT_PERIOD_DEFAULT);
        editor.putString(ConfigConstants.PAUSE_PERIOD, ConfigConstants.SETTING_PAUSE_PERIOD_DEFAULT);
        editor.putString(ConfigConstants.SPACE_BETWEEN_FREQUENCIES, ConfigConstants.SETTING_SPACE_BETWEEN_FREQUENCIES_DEFAULT);
        editor.putString(ConfigConstants.NUMBER_OF_BYTES, ConfigConstants.SETTING_NUMBER_OF_BYTES_DEFAULT);
        editor.putString(ConfigConstants.NUMBER_OF_MESSAGE_BLOCKS, nMessageBlocks + "");
        editor.putString(ConfigConstants.LOUDNESS, ConfigConstants.SETTING_LOUDNESS_DEFAULT);
        editor.apply();
        editor.commit();

    }

    protected void sendData(String data) {

        /*JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("date", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }*/
        result = new PluginResult(PluginResult.Status.OK, data);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }
}
