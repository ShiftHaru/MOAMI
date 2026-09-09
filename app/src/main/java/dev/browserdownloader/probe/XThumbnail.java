package dev.browserdownloader.probe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.net.*;
import java.io.*;

final class XThumbnail {
    static boolean permitted(String value) {
        try { URI u=URI.create(value);return "https".equals(u.getScheme()) && "pbs.twimg.com".equals(u.getHost())
                && u.getUserInfo()==null && u.getPort()==-1 && u.getRawFragment()==null; }
        catch(Exception e){return false;}
    }
    static Bitmap load(String url) throws Exception {
        if(!permitted(url))throw new IOException("미리보기 주소 없음");
        HttpURLConnection c=(HttpURLConnection)URI.create(url).toURL().openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(10000);
        var timeout=new java.util.Timer(true);
        timeout.schedule(new java.util.TimerTask(){public void run(){c.disconnect();}},30000);
        try {
            if(c.getResponseCode()!=200 || c.getContentType()==null || !c.getContentType().startsWith("image/"))throw new IOException();
            if(c.getContentLengthLong()>20*1024*1024)throw new IOException();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                    if(bytes.size()+n>20*1024*1024)throw new IOException();bytes.write(b,0,n);
                }
            }
            byte[] data=bytes.toByteArray();var opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(data,0,data.length,opts);
            if(opts.outWidth<=0||opts.outHeight<=0||(long)opts.outWidth*opts.outHeight>100_000_000)throw new IOException();
            opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>512)opts.inSampleSize*=2;
            opts.inJustDecodeBounds=false;Bitmap result=BitmapFactory.decodeByteArray(data,0,data.length,opts);
            if(result==null)throw new IOException();return result;
        } finally {timeout.cancel();c.disconnect();}
    }
}
