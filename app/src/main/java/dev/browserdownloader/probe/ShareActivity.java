package dev.browserdownloader.probe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** One share target; route validated X and Instagram posts to the media popup. */
public final class ShareActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Intent incoming=getIntent();
            if (!Intent.ACTION_SEND.equals(incoming.getAction()) || !"text/plain".equals(incoming.getType())) return;
            String text=incoming.getStringExtra(Intent.EXTRA_TEXT);
            String link=XShareLink.extract(text);
            Intent next=link.isEmpty()
                    ? new Intent(this,MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text)
                    : new Intent(this,XShareActivity.class).putExtra("post",link);
            startActivity(next);
        } catch (RuntimeException invalid) {
            android.widget.Toast.makeText(this,"공유 주소를 읽지 못했습니다.",android.widget.Toast.LENGTH_LONG).show();
        } finally { finish(); }
    }
}
