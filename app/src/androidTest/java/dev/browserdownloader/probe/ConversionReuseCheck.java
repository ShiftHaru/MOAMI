package dev.browserdownloader.probe;

import android.app.*;
import android.os.Bundle;
import dev.browserdownloader.xprobe.GifConversion;
import java.io.*;
import java.nio.file.*;
import org.json.JSONObject;

/** Self-authored input; tests identity and invalidation without contacting X. */
final class ConversionReuseCheck {
    static void run(Instrumentation test) {
        Bundle out=new Bundle();int code=Activity.RESULT_CANCELED;File directory=null;
        try {
            directory=Files.createTempDirectory(test.getTargetContext().getCacheDir().toPath(),"conversion-reuse-").toFile();
            File source=new File(directory,"input.mp4"),gif=new File(directory,"output.gif"),metadata=new File(directory,"output.gif.json");
            try(InputStream input=test.getTargetContext().getAssets().open("self_authored.mp4")){Files.copy(input,source.toPath());}
            require(!GifConversion.convertForDownload(source,gif).getBoolean("reusedConversion"),"first conversion reused");
            byte[] original=Files.readAllBytes(gif.toPath());
            require(GifConversion.convertForDownload(source,gif).getBoolean("reusedConversion"),"matching conversion not reused");
            require(java.util.Arrays.equals(original,Files.readAllBytes(gif.toPath())),"reuse changed bytes");
            Files.write(gif.toPath(),new byte[]{1,2,3});
            require(!GifConversion.convertForDownload(source,gif).getBoolean("reusedConversion"),"corrupt GIF reused");
            JSONObject cached=new JSONObject(Files.readString(metadata.toPath()));cached.put("settingsVersion",0);Files.writeString(metadata.toPath(),cached.toString());
            require(!GifConversion.convertForDownload(source,gif).getBoolean("reusedConversion"),"old settings reused");
            Files.write(source.toPath(),new byte[]{0,0,0,8,'f','r','e','e'},StandardOpenOption.APPEND);
            require(!GifConversion.convertForDownload(source,gif).getBoolean("reusedConversion"),"changed source reused");
            require(android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(gif)) instanceof android.graphics.drawable.AnimatedImageDrawable,"invalid regenerated GIF");
            out.putString("report","PASS: exact input/settings/output reuse; changed input, old settings and corrupt GIF regenerated; animated output");code=Activity.RESULT_OK;
        }catch(Throwable e){out.putString("report","FAIL reuse: "+e.getClass().getSimpleName()+(e instanceof AssertionError?" "+e.getMessage():""));}
        finally {
            if(directory!=null)try {
                for(String name:new String[]{"input.mp4","output.gif","output.gif.partial","output.gif.json","output.gif.json.new","output.gif.json.bak"})Files.deleteIfExists(new File(directory,name).toPath());
                Files.delete(directory.toPath());
            }catch(IOException e){code=Activity.RESULT_CANCELED;out.putString("report","FAIL reuse fixture cleanup");}
        }
        test.finish(code,out);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
