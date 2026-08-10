package com.aetherspot.app;

import android.app.*;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProxyService extends Service {
    public static final String START="com.aetherspot.START", STOP="com.aetherspot.STOP";
    public static volatile boolean running=false;
    public static volatile int connections=0;
    public static volatile long rx=0,tx=0;
    private ProxyServer server;
    @Override public void onCreate(){ super.onCreate(); if(Build.VERSION.SDK_INT>=26){ NotificationChannel c=new NotificationChannel("proxy","AetherSpot sharing",NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); } }
    @Override public int onStartCommand(Intent in,int flags,int id){ if(in!=null&&STOP.equals(in.getAction())){stopProxy();return START_NOT_STICKY;} startFg(); if(server==null){rx=tx=0; server=new ProxyServer(8282); server.start(); running=true;} return START_STICKY; }
    private void startFg(){ Intent open=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"proxy"):new Notification.Builder(this); b.setContentTitle("AetherSpot is sharing").setContentText("HTTP proxy listening on port 8282").setSmallIcon(android.R.drawable.stat_sys_upload_done).setOngoing(true).setContentIntent(pi); startForeground(8282,b.build()); }
    private void stopProxy(){ if(server!=null)server.close(); server=null; running=false; connections=0; stopForeground(true); stopSelf(); }
    @Override public void onDestroy(){ if(server!=null)server.close(); server=null; running=false; super.onDestroy(); }
    @Override public IBinder onBind(Intent i){return null;}

    private static class ProxyServer {
        final int port; final AtomicBoolean live=new AtomicBoolean(); ServerSocket ss; final ExecutorService pool=Executors.newCachedThreadPool();
        ProxyServer(int p){port=p;}
        void start(){ live.set(true); pool.execute(()->{try{ss=new ServerSocket();ss.setReuseAddress(true);ss.bind(new InetSocketAddress("0.0.0.0",port));while(live.get()){Socket c=ss.accept();pool.execute(()->handle(c));}}catch(IOException ignored){}}); }
        void handle(Socket client){ connections++; try(client){ client.setSoTimeout(30000); BufferedInputStream cin=new BufferedInputStream(client.getInputStream()); OutputStream cout=client.getOutputStream(); ByteArrayOutputStream hb=new ByteArrayOutputStream(); int prev=0,cur; while(hb.size()<65536&&(cur=cin.read())!=-1){hb.write(cur);if(prev=='\r'&&cur=='\n'){byte[] a=hb.toByteArray();int n=a.length;if(n>=4&&a[n-4]=='\r'&&a[n-3]=='\n'&&a[n-2]=='\r'&&a[n-1]=='\n')break;}prev=cur;} String header=hb.toString(StandardCharsets.ISO_8859_1.name()); String[] lines=header.split("\\r?\\n"); if(lines.length==0)return; String[] first=lines[0].split(" "); if(first.length<2)return; if("CONNECT".equalsIgnoreCase(first[0])){ String target=first[1];int colon=target.lastIndexOf(':');String host=colon>0?target.substring(0,colon):target;int p=colon>0?Integer.parseInt(target.substring(colon+1)):443; try(Socket remote=new Socket()){remote.connect(new InetSocketAddress(host,p),15000);cout.write("HTTP/1.1 200 Connection Established\r\nProxy-Agent: AetherSpot\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));cout.flush();relay(client,cin,remote);} } else { String host=null;int p=80;for(String l:lines)if(l.toLowerCase().startsWith("host:")){host=l.substring(5).trim();break;} if(host==null)return;if(host.contains(":")){String[] hp=host.split(":",2);host=hp[0];p=Integer.parseInt(hp[1]);} try(Socket remote=new Socket()){remote.connect(new InetSocketAddress(host,p),15000);OutputStream rout=remote.getOutputStream();byte[] raw=hb.toByteArray();rout.write(raw);rout.flush();tx+=raw.length;relay(client,cin,remote);} } }catch(Exception ignored){}finally{connections--;}}
        void relay(Socket client,InputStream cin,Socket remote)throws Exception{ CountDownLatch done=new CountDownLatch(2); pool.execute(()->copy(cin,remote,done,true)); pool.execute(()->copyRemote(remote,client,done)); done.await(); }
        void copy(InputStream in,Socket outSock,CountDownLatch d,boolean upload){try{OutputStream out=outSock.getOutputStream();byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){out.write(b,0,n);out.flush();if(upload)tx+=n;else rx+=n;}}catch(Exception ignored){}finally{try{outSock.shutdownOutput();}catch(Exception ignored){}d.countDown();}}
        void copyRemote(Socket remote,Socket client,CountDownLatch d){try{InputStream in=remote.getInputStream();OutputStream out=client.getOutputStream();byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){out.write(b,0,n);out.flush();rx+=n;}}catch(Exception ignored){}finally{try{client.shutdownOutput();}catch(Exception ignored){}d.countDown();}}
        void close(){live.set(false);try{if(ss!=null)ss.close();}catch(Exception ignored){}pool.shutdownNow();}
    }
}
