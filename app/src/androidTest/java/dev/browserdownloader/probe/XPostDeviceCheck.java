package dev.browserdownloader.probe;

import android.app.*;
import android.os.Bundle;
import dev.browserdownloader.xprobe.NativeProbe;
import dev.browserdownloader.xprobe.PublicDownloads;
import org.json.*;
import java.io.InputStream;

/** Opt-in user-authorized public post; checks the actual published bytes. */
final class XPostDeviceCheck {
    static void run(Instrumentation test,String link) {
        Bundle out=new Bundle();
        try {
            JSONObject report=new NativeProbe(test.getTargetContext()).downloadX(link,true);
            out.putString("result",report.toString());
            if(!report.getBoolean("allSaved")) throw new Exception("not all media saved");
            JSONArray items=report.getJSONArray("items");
            int photos=0,videos=0;
            for(int i=0;i<items.length();i++) {
                JSONObject item=items.getJSONObject(i);
                if(item.optString("kind").equals("photo")) photos++;
                if(item.optString("kind").equals("video")) videos++;
                try(InputStream input=test.getTargetContext().getContentResolver().openInputStream(android.net.Uri.parse(item.getString("savedUri")))) {
                    if(!item.getString("sha256").equals(PublicDownloads.sha256(input))) throw new Exception("published hash mismatch");
                }
            }
            out.putString("report","PASS: published hash checks; photos="+photos+" videos="+videos);
            test.finish(Activity.RESULT_OK,out);
        } catch(Exception e) { out.putString("report","FAIL: "+e.getClass().getSimpleName()); test.finish(Activity.RESULT_CANCELED,out); }
    }
}
