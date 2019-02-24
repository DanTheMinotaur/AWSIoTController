package danshub.online.iotcontroller;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttLastWillAndTestament;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    static final String LOG_TAG = MainActivity.class.getCanonicalName();
    static final String commandTopic = "Android/Command";
    static final String dataTopic = "PI/Data";

    // IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "a17mh16tz1p39u-ats.iot.eu-west-1.amazonaws.com";
    // Name of the AWS IoT policy to attach to a newly created certificate
    private static final String AWS_IOT_POLICY_NAME = "IOTAndroidPolicyFinal";

    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.EU_WEST_1;
    // Filename of KeyStore file on the filesystem
    private static final String KEYSTORE_NAME = "iot_ca1_keystore";
    // Password for the private key in the KeyStore
    private static final String KEYSTORE_PASSWORD = "Hello123";
    // Certificate and key aliases in the KeyStore
    private static final String CERTIFICATE_ID = "default";

    AWSIotClient mIotAndroidClient;
    AWSIotMqttManager mqttManager;
    String clientId;
    String keystorePath;
    String keystoreName;
    String keystorePassword;

    KeyStore clientKeyStore = null;
    String certificateId;

    JSONObject receievedIoTJSONdata;

    private HashMap<String, TextView> displayDataTextViews;
    private TextView motionEntry, soundEntry, lightEntry;
    private TextView motionBroadcastRateTextView, soundBroadcastRateTextView, lightBroadcastRateTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        clientId = UUID.randomUUID().toString();

        AWSMobileClient.getInstance().initialize(this, new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails result) {
                initIoTClient();

            }

            @Override
            public void onError(Exception e) {
                Log.e(LOG_TAG, "onError: ", e);
            }
        });

        createButtons();
        createSwitches();
        createTextViews();
        createSliders();
    }

    public void createTextViews() {
        displayDataTextViews = new HashMap<>();
        displayDataTextViews.put("motion", (TextView) findViewById(R.id.motionEntry));

        motionBroadcastRateTextView = findViewById(R.id.motionBroadcastRateTextView);
        soundBroadcastRateTextView = findViewById(R.id.soundBroadcastRateTextView);
        lightBroadcastRateTextView = findViewById(R.id.lightBroadcastRateTextView);

        motionBroadcastRateTextView.setText("");
        soundBroadcastRateTextView.setText("");
        lightBroadcastRateTextView.setText("");
    }

    public JSONObject buildCommand(String command, String value) {
        try {
            return new JSONObject().put("command", new JSONObject().put(command, value));
        } catch (Exception e) {
            Log.e(LOG_TAG, e.toString());
            return null;
        }
    }

    public JSONObject buildCommand(String command, int value) {
        try {
            return new JSONObject().put("command", new JSONObject().put(command, value));
        } catch (Exception e) {
            Log.e(LOG_TAG, e.toString());
            return null;
        }
    }

    public JSONObject buildCommand(String command, String subKey, String value) {
        try {
            JSONObject subcommandJson = new JSONObject().put(subKey, value);
            return new JSONObject().put("command", new JSONObject().put(command, subcommandJson));
        } catch (Exception e) {
            Log.e(LOG_TAG, e.toString());
            return null;
        }
    }

    public JSONObject buildCommand(String command, String subKey, int value) {
        try {
            JSONObject subcommandJson = new JSONObject().put(subKey, value);
            return new JSONObject().put("command", new JSONObject().put(command, subcommandJson));
        } catch (Exception e) {
            Log.e(LOG_TAG, e.toString());
            return null;
        }
    }
    /**
     * Method creates listener events for controlling switches
     */
    public void createSwitches() {
        HashMap<String, Switch> switches = new HashMap<>();
        switches.put("motion", (Switch) findViewById(R.id.pir_switch));
        switches.put("sound", (Switch) findViewById(R.id.sound_switch));
        switches.put("light", (Switch) findViewById(R.id.light_switch));

        Iterator map = switches.entrySet().iterator();

        while (map.hasNext()) {
            Map.Entry current = (Map.Entry)map.next();
            Switch currentSwitch = (Switch) current.getValue();
            final String switchType = (String) current.getKey();

            currentSwitch.setChecked(false);

            currentSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    String switchName = switchType.toUpperCase();
                    if (isChecked) {
                        Log.v(LOG_TAG, switchName + " Switch On");
                        publish(buildCommand(switchType, "on"), commandTopic);
                    } else {
                        Log.v(LOG_TAG, switchName + " Switch Off");
                        publish(buildCommand(switchType, "off"), commandTopic);
                    }
                }
            });
        }
    }

    public void createButtons() {
        FloatingActionButton alarmButton = findViewById(R.id.buzzerButton);

        alarmButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    Log.v(LOG_TAG, "Alarm Button Press");
                    publish(buildCommand("buzzer", "on"), commandTopic);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    Log.v(LOG_TAG, "Alarm Button Lift");
                    publish(buildCommand("buzzer", "off"), commandTopic);
                }
                return true;
            }
        });
    }

    public void createSliders() {
        HashMap<String, SeekBar> broadcastSliders = new HashMap<>();
        broadcastSliders.put("motion", (SeekBar) findViewById(R.id.motionBroadcastRateSlider));
        broadcastSliders.put("sound", (SeekBar) findViewById(R.id.soundBroadcastRateSlider));
        broadcastSliders.put("light", (SeekBar) findViewById(R.id.lightBroadcastRateSlider));

        Iterator map = broadcastSliders.entrySet().iterator();

        while (map.hasNext()) {
            Map.Entry slider = (Map.Entry)map.next();
            final SeekBar bar = (SeekBar) slider.getValue();
            final String barType = (String) slider.getKey();
            bar.setMax(100);
            bar.setProgress(10);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int progress = 0;
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    this.progress = progress;
                    if (progress == 0) {
                        this.progress = 1;
                    }
                    Log.v(LOG_TAG, "Current Progress for " + barType + " slider: " + this.progress);

                    String currentRate = this.progress + "s";
                    if(barType.equals("motion")) {
                        motionBroadcastRateTextView.setText(currentRate);
                    } else if (barType.equals("sound")) {
                        soundBroadcastRateTextView.setText(currentRate);
                    } else if (barType.equals("light")) {
                        lightBroadcastRateTextView.setText(currentRate);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    publish(buildCommand(barType, "broadcast_rate", this.progress), commandTopic);
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void renderJsonData(JSONObject data) {
        Log.v(LOG_TAG, data.toString());
        try {
            if (data.has("data")) {
                HashMap<String, TextView> dataTypes = new HashMap<>(displayDataTextViews);
                Iterator map = dataTypes.entrySet().iterator();

                JSONObject received_data = (JSONObject) data.get("data");
                Log.v(LOG_TAG, "Json has data key");

                while (map.hasNext()) {
                    Map.Entry current = (Map.Entry)map.next();
                    String key = (String) current.getKey();
                    if(received_data.has(key)) {
                        TextView viewToUpdate = (TextView) current.getValue();
                        Boolean dataValue = (Boolean) received_data.get(key);
                        String result;
                        if(dataValue) {
                            result = key.toUpperCase() + " Detected!";
                        } else {
                            result = "No " + key.toUpperCase() + " Detected!";
                        }
                        viewToUpdate.setText(result);
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.toString());
        }
    }

    public void publish(JSONObject data, String topic) {
        try {
            mqttManager.publishString(data.toString(), topic, AWSIotMqttQos.QOS0);
            Log.v(LOG_TAG, "Publish " + data + " to AWS");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Publish error.", e);
        }
    }

    public void subscribe(String topic) {

        Log.d(LOG_TAG, "topic = " + topic);

        try {
            mqttManager.subscribeToTopic(topic, AWSIotMqttQos.QOS0,
                    new AWSIotMqttNewMessageCallback() {
                        @Override
                        public void onMessageArrived(final String topic, final byte[] data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String message = new String(data, "UTF-8");
                                        Log.d(LOG_TAG, "Message arrived:");
                                        Log.d(LOG_TAG, "   Topic: " + topic);
                                        Log.d(LOG_TAG, " Message: " + message);
                                        try {
                                            receievedIoTJSONdata = new JSONObject(message);
                                            renderJsonData(receievedIoTJSONdata);
                                        } catch (JSONException e) {
                                            Log.e(LOG_TAG, "Invalid Json Message ", e);
                                        }

                                    } catch (UnsupportedEncodingException e) {
                                        Log.e(LOG_TAG, "Message encoding error.", e);
                                    }
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            Log.e(LOG_TAG, "Subscription error.", e);
        }
    }

    /**
     * Method Disconnects MQTT Client
     */
    public void disconnectClient() {
        try {
            mqttManager.disconnect();
            Log.v(LOG_TAG, "MQTT Client Disconnected");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Disconnect error.", e);
        }
    }

    /**
     * Method connects to MQTT AWS Service
     */
    public Boolean connectClient() {
        Log.d(LOG_TAG, "clientId = " + clientId);

        try {
            mqttManager.connect(clientKeyStore, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable) {
                    Log.d(LOG_TAG, "Status = " + String.valueOf(status));

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.v(LOG_TAG, status.toString());
                            if (throwable != null) {
                                Log.e(LOG_TAG, "Connection error.", throwable);
                            }
                        }
                    });
                }
            });
        } catch (final Exception e) {
            Log.e(LOG_TAG, "Connection error.", e);
            return false;
        }
        return true;
    }

    void initIoTClient() {
        Region region = Region.getRegion(MY_REGION);

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);

        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.setKeepAlive(10);

        // Set Last Will and Testament for MQTT.  On an unclean disconnect (loss of connection)
        // AWS IoT will publish this message to alert other clients.
        AWSIotMqttLastWillAndTestament lwt = new AWSIotMqttLastWillAndTestament("my/lwt/topic",
                "Android client lost connection", AWSIotMqttQos.QOS0);
        mqttManager.setMqttLastWillAndTestament(lwt);

        // IoT Client (for creation of certificate if needed)
        mIotAndroidClient = new AWSIotClient(AWSMobileClient.getInstance());
        mIotAndroidClient.setRegion(region);

        keystorePath = getFilesDir().getPath();
        keystoreName = KEYSTORE_NAME;
        keystorePassword = KEYSTORE_PASSWORD;
        certificateId = CERTIFICATE_ID;

        // To load cert/key from keystore on filesystem
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, keystoreName)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(certificateId, keystorePath,
                        keystoreName, keystorePassword)) {
                    Log.i(LOG_TAG, "Certificate " + certificateId
                            + " found in keystore - using for MQTT.");
                    // load keystore from file into memory to pass on connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword);
                    /* initIoTClient is invoked from the callback passed during AWSMobileClient initialization.
                    The callback is executed on a background thread so UI update must be moved to run on UI Thread. */
                } else {
                    Log.i(LOG_TAG, "Key/cert " + certificateId + " not found in keystore.");
                }
            } else {
                Log.i(LOG_TAG, "Keystore " + keystorePath + "/" + keystoreName + " not found.");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "An error occurred retrieving cert/key from keystore.", e);
        }

        if (clientKeyStore == null) {
            Log.i(LOG_TAG, "Cert/key was not found in keystore - creating new key and certificate.");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Create a new private key and certificate. This call
                        // creates both on the server and returns them to the
                        // device.
                        CreateKeysAndCertificateRequest createKeysAndCertificateRequest =
                                new CreateKeysAndCertificateRequest();
                        createKeysAndCertificateRequest.setSetAsActive(true);
                        final CreateKeysAndCertificateResult createKeysAndCertificateResult;
                        createKeysAndCertificateResult =
                                mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest);
                        Log.i(LOG_TAG,
                                "Cert ID: " +
                                        createKeysAndCertificateResult.getCertificateId() +
                                        " created.");

                        // store in keystore for use in MQTT client
                        // saved as alias "default" so a new certificate isn't
                        // generated each run of this application
                        AWSIotKeystoreHelper.saveCertificateAndPrivateKey(certificateId,
                                createKeysAndCertificateResult.getCertificatePem(),
                                createKeysAndCertificateResult.getKeyPair().getPrivateKey(),
                                keystorePath, keystoreName, keystorePassword);

                        // load keystore from file into memory to pass on
                        // connection
                        clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                                keystorePath, keystoreName, keystorePassword);

                        // Attach a policy to the newly created certificate.
                        // This flow assumes the policy was already created in
                        // AWS IoT and we are now just attaching it to the
                        // certificate.
                        AttachPrincipalPolicyRequest policyAttachRequest =
                                new AttachPrincipalPolicyRequest();
                        policyAttachRequest.setPolicyName(AWS_IOT_POLICY_NAME);
                        policyAttachRequest.setPrincipal(createKeysAndCertificateResult
                                .getCertificateArn());
                        mIotAndroidClient.attachPrincipalPolicy(policyAttachRequest);

                    } catch (Exception e) {
                        Log.e(LOG_TAG,
                                "Exception occurred when generating new private key and certificate.",
                                e);
                    }
                }
            }).start();
        }
    }
}
