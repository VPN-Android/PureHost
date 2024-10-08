package top.nicelee.purehost;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.koin.java.KoinJavaComponent;

import top.nicelee.purehost.vpn.LocalVpnServiceKT;
import top.nicelee.purehost.vpn.VpnViewModel;
import top.nicelee.purehost.vpn.config.ConfigReader;

public class MainActivity extends Activity {
    Intent serviceIntent;
    Button btnStart;
    Button btnEnd;
    Button btnWriteHost;
    EditText textHost;

    private VpnViewModel mViewModel = KoinJavaComponent.get(VpnViewModel.class);

    public static String path;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        path = getFilesDir().getAbsolutePath();
        genHostFirst();
        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);
        textHost = findViewById(R.id.hostConfig);

//        textDNS1 = findViewById(R.id.dnsConfig);
//        textDNS2 = findViewById(R.id.dnsConfig2);

        btnStart = findViewById(R.id.btnStart);

        Button getButton = findViewById(R.id.http_get);
        getButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    new HttpGetTest().get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVPN();
            }
        });

        btnEnd = findViewById(R.id.btnEnd);
        btnEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopVPN();
            }
        });
        btnEnd.setEnabled(false);

//        btnSetDNS = findViewById(R.id.btnDNSSet);
//        btnSetDNS.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                String dns1 = textDNS1.getText().toString();
//                String dns2 = textDNS2.getText().toString();
//                if(dns1.trim().isEmpty()){
//                    Toast.makeText(MainActivity.this, "DNS设置为空!", Toast.LENGTH_SHORT).show();
//                }else {
//                    ConfigReader.dnsList.add(textDNS1.getText().toString());
//                    if(!dns2.trim().isEmpty()){
//                        ConfigReader.dnsList.add(textDNS2.getText().toString());
//                    }
//                    Toast.makeText(MainActivity.this, "DNS设置完成!", Toast.LENGTH_SHORT).show();
//                }
//            }
//        });
//
//        btnClearDNS = findViewById(R.id.btnDNSClear);
//        btnClearDNS.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                ConfigReader.dnsList.clear();
//                textDNS1.clearComposingText();
//                Toast.makeText(MainActivity.this, "已设置成使用默认DNS设置!", Toast.LENGTH_SHORT).show();
//            }
//        });

//        btnReadHost = findViewById(R.id.btnHostRead);
//        btnReadHost.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                ConfigReader.readHost(textHost);
//                Toast.makeText(MainActivity.this, "Host读取完成!", Toast.LENGTH_SHORT).show();
//            }
//        });
        //btnReadHost.setEnabled(false);

        btnWriteHost = findViewById(R.id.btnHostWrite);
        btnWriteHost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConfigReader.writeHost(textHost);
                Toast.makeText(MainActivity.this, "Host写入完成!", Toast.LENGTH_SHORT).show();
            }
        });
        //btnWriteHost.setEnabled(false);


        //ConfigReader.writeHost();
        ConfigReader.readHost(textHost);
       // ConfigReader.initDNS(this);
        //TextView txtHeader = findViewById(R.id.textMySite);
        //txtHeader.setFocusable(true);
        //txtHeader.requestFocus();
        //textDNS1.clearFocus();
    }

    private void stopVPN(){
        //serviceIntent = new Intent(this, LocalVpnService.class);
        //stopService(serviceIntent);
//        LocalVpnServiceKT.Companion.getInstance().stopVPN();
        mViewModel.stopVPN();
        btnStart.setEnabled(true);
        btnEnd.setEnabled(false);
        btnWriteHost.setEnabled(true);
    }
    private void startVPN()
    {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        serviceIntent = new Intent(this, LocalVpnServiceKT.class);
        startService(serviceIntent);
        btnStart.setEnabled(false);
        btnEnd.setEnabled(true);
        btnWriteHost.setEnabled(false);
    }

        private void genHostFirst(){
            SharedPreferences shared=getSharedPreferences("is", MODE_PRIVATE);
            boolean isfer=shared.getBoolean("is_first_init", true);
            SharedPreferences.Editor editor=shared.edit();
            if(isfer){
                ConfigReader.initHost();
                editor.putBoolean("is_first_init", false);
                editor.apply();
            }
        }

}
