package com.aetherspot.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {
    private TextView status, endpoint, stats;
    private Button toggle;
    private final Runnable updater = new Runnable() {
        @Override public void run() {
            boolean running = ProxyService.running;
            status.setText(running ? "● ACTIVE" : "○ STOPPED");
            status.setTextColor(running ? Color.rgb(25,135,84) : Color.DKGRAY);
            toggle.setText(running ? "STOP SHARING" : "START SHARING");
            endpoint.setText("HTTP / HTTPS CONNECT\n" + findIp() + ":8282");
            stats.setText("Connections  " + ProxyService.connections + "\nDownload     " + format(ProxyService.rx) + "\nUpload       " + format(ProxyService.tx) + "\nTotal        " + format(ProxyService.rx + ProxyService.tx));
            status.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        setContentView(makeUi());
        updater.run();
    }

    private View makeUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(28),dp(24),dp(28)); root.setBackgroundColor(Color.rgb(244,247,250));
        TextView title = text("AetherSpot", 32, true); root.addView(title);
        TextView sub = text("Universal Android internet sharing", 16, false); sub.setTextColor(Color.GRAY); root.addView(sub, lp(-1,dp(52)));
        status = text("○ STOPPED", 24, true); status.setGravity(Gravity.CENTER); root.addView(status, lp(-1,dp(72)));
        toggle = new Button(this); toggle.setText("START SHARING"); toggle.setTextSize(16); toggle.setOnClickListener(v -> {
            Intent i = new Intent(this, ProxyService.class).setAction(ProxyService.running ? ProxyService.STOP : ProxyService.START);
            if (!ProxyService.running && Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        }); root.addView(toggle, lp(-1,dp(58)));
        root.addView(space(22));
        endpoint = cardText(); root.addView(endpoint, lp(-1,dp(100)));
        root.addView(space(14));
        stats = cardText(); root.addView(stats, lp(-1,dp(150)));
        root.addView(space(18));
        TextView guide = cardText(); guide.setText("ANDROID CLIENT SETUP\n\n1. Connect client to this phone's hotspot / same Wi‑Fi.\n2. Open Wi‑Fi network details.\n3. Proxy → Manual.\n4. Host = address shown above.\n5. Port = 8282.\n6. Save and test HTTPS in Chrome.\n\nNo client app required."); root.addView(guide, lp(-1,dp(270)));
        scroll.addView(root); return scroll;
    }
    private TextView cardText() { TextView t=text("",16,false); t.setPadding(dp(20),dp(18),dp(20),dp(18)); t.setBackgroundColor(Color.WHITE); return t; }
    private TextView text(String s,int size,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(25,30,38)); if(bold)t.setTypeface(null,1); return t; }
    private View space(int d){ View v=new View(this); v.setLayoutParams(lp(1,dp(d))); return v; }
    private LinearLayout.LayoutParams lp(int w,int h){ return new LinearLayout.LayoutParams(w,h); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    private String findIp(){ try { for(NetworkInterface ni: Collections.list(NetworkInterface.getNetworkInterfaces())) for(java.net.InetAddress a:Collections.list(ni.getInetAddresses())) if(!a.isLoopbackAddress() && a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress(); }catch(Exception ignored){} return "192.168.x.x"; }
    private static String format(long n){ double v=n; String[] u={"B","KB","MB","GB"}; int i=0; while(v>=1024&&i<3){v/=1024;i++;} return i==0? n+" B":String.format("%.1f %s",v,u[i]); }
    @Override protected void onDestroy(){ status.removeCallbacks(updater); super.onDestroy(); }
}
