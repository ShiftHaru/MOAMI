"""Run the production address state machine against a small Android event/node double.

Requires a JDK (JAVA_HOME or javac on PATH). This checks branching/ownership, not
Chrome's real accessibility tree; the final release still needs a device check.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class ChromePageAddressTest(unittest.TestCase):
    def test_address_lookup_and_window_ownership(self):
        jdk = Path(os.environ.get('JAVA_HOME', '')) / 'bin'
        javac = shutil.which(str(jdk / 'javac')) or shutil.which('javac')
        self.assertIsNotNone(javac, 'Set JAVA_HOME to a JDK to run the address regression')
        java = str(Path(javac).with_name('java.exe' if os.name == 'nt' else 'java'))
        sources = {
            'android/os/Looper.java': '''package android.os;
public class Looper { public static Looper getMainLooper() { return new Looper(); } }''',
            'android/os/SystemClock.java': '''package android.os;
public class SystemClock { public static long now; public static long elapsedRealtime(){return now;} }''',
            'android/os/Handler.java': '''package android.os;
public class Handler {
  public static Runnable next;
  public Handler(Looper l) {}
  public void postDelayed(Runnable r,long delay) {next=r;}
  public void removeCallbacksAndMessages(Object o) {next=null;}
  public static void tick() {SystemClock.now+=150; Runnable r=next;next=null;if(r!=null)r.run();}
}''',
            'android/view/accessibility/AccessibilityNodeInfo.java': '''package android.view.accessibility;
import java.util.*;
public class AccessibilityNodeInfo {
  public static final int ACTION_CLICK=16;
  public String pkg="com.android.chrome", text;
  public AccessibilityNodeInfo document;
  public java.util.function.BooleanSupplier action;
  public final Map<String,AccessibilityNodeInfo> nodes=new HashMap<>();
  public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo n){return n;}
  public void recycle(){}
  public CharSequence getPackageName(){return pkg;}
  public CharSequence getText(){return text;}
  public boolean isPassword(){return false;}
  public boolean isClickable(){return action!=null;}
  public boolean performAction(int a){return action.getAsBoolean();}
  public AccessibilityNodeInfo getParent(){return null;}
  public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByViewId(String id){
    var n=nodes.get(id.substring(id.indexOf('/')+1));return n==null?List.of():List.of(n);
  }
}''',
            'dev/browserdownloader/probe/ChromeProbeService.java': '''package dev.browserdownloader.probe;
import android.view.accessibility.AccessibilityNodeInfo;
class ChromeProbeService {static AccessibilityNodeInfo findDocument(AccessibilityNodeInfo r){return r.document;}}''',
            'dev/browserdownloader/probe/AddressRegression.java': '''package dev.browserdownloader.probe;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

public class AddressRegression {
  static AccessibilityNodeInfo root, page, info;
  static ChromePageAddress lookup;
  static List<String> results;
  static int backs, closes;
  static String url;
  static boolean backSucceeds;
  static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
  static AccessibilityNodeInfo node(String text){var n=new AccessibilityNodeInfo();n.text=text;return n;}
  static void setup(boolean close,String address){
    Handler.next=null;SystemClock.now=0;backs=closes=0;backSucceeds=true;url=address;
    page=node("");page.document=node("document");page.nodes.put("url_bar",node("example.test/path"));
    info=node("");
    var truncated=node("example.test");
    truncated.action=()->{info.nodes.remove("page_info_truncated_url");info.nodes.put("page_info_url",node(url));return true;};
    info.nodes.put("page_info_truncated_url",truncated);
    if(close){var button=node("");button.action=()->{closes++;root=page;return true;};info.nodes.put("page_info_close",button);}
    var icon=node("");icon.action=()->{root=info;return true;};page.nodes.put("location_bar_status_icon",icon);
    root=page;results=new ArrayList<>();
    lookup=new ChromePageAddress(()->root,()->{backs++;if(backSucceeds)root=page;return backSucceeds;},results::add);
    lookup.start();
  }
  static void drain(){for(int i=0;i<40&&Handler.next!=null;i++)Handler.tick();require(Handler.next==null,"bounded lookup");}
  public static void main(String[] args){
    for(boolean close:new boolean[]{true,false})for(String address:List.of("http://example.test/path?q=1#f","https://example.test/path?q=1#f")){
      setup(close,address);drain();require(results.equals(List.of(address)),"exact URL");
      require(backs==(close?0:1)&&closes==(close?1:0),"one close, prefer button");
    }
    setup(false,"https://example.test/path");root=null;Handler.tick();root=info;drain();require(results.equals(List.of(url)),"transient null root");
    setup(false,"https://example.test/path");Handler.tick();root=node("other window");drain();require(results.equals(List.of(""))&&backs==0,"window switch");
    setup(false,"https://example.test/path");Handler.tick();info.pkg="other.app";drain();require(results.equals(List.of(""))&&backs==0,"other package");
    setup(false,"https://example.test/path");Handler.tick();Handler.tick();page.document=node("new document");drain();require(results.equals(List.of("")),"changed document");
    setup(false,"https://example.test/path");Handler.tick();Handler.tick();page.nodes.get("url_bar").text="other.test";drain();require(results.equals(List.of("")),"changed URL bar");
    setup(false,"not a URL");drain();require(results.equals(List.of(""))&&backs==1,"timeout cleans owned dialog");
    setup(false,"https://example.test/path");Handler.tick();lookup.cancel();lookup.cancel();require(backs==1&&results.isEmpty()&&Handler.next==null,"cancel once");
    setup(false,"https://example.test/path");Handler.tick();info.nodes.clear();lookup.cancel();require(backs==0,"same root without page-info is not owned");
    setup(false,"https://example.test/path");Handler.tick();root=page;lookup.cancel();require(backs==0,"cancel never navigates page");
    setup(false,"https://example.test/path");backSucceeds=false;drain();require(backs==1&&results.equals(List.of("")),"failed BACK not repeated by cancel");
    // A window can change between reading the URL and acquiring a fresh root for BACK.
    setup(false,"https://example.test/path");lookup.cancel();root=page;results.clear();backs=0;
    int[] reads={0};
    lookup=new ChromePageAddress(()->{if(++reads[0]==4)root=page;return root;},()->{backs++;return true;},results::add);
    lookup.start();drain();require(results.equals(List.of(""))&&backs==0,"recheck before BACK");
    System.out.println("PASS address lookup: optional close, exact scheme/query, timeout, cancellation and window ownership");
  }
}''',
        }
        repo = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            for name, content in sources.items():
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content, encoding='utf-8')
            production = repo / 'app/src/main/java/dev/browserdownloader/probe'
            for name in ('ChromePageAddress.java', 'PreviewRules.java'):
                shutil.copyfile(production / name, folder / 'dev/browserdownloader/probe' / name)
            subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(folder),
                            *map(str, folder.rglob('*.java'))], check=True, capture_output=True)
            result = subprocess.run([java, '-cp', str(folder),
                                     'dev.browserdownloader.probe.AddressRegression'],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
