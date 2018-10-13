package com.virjar.retal_driver;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Created by virjar on 2018/10/13.
 * <br>
 */

public class NotSupportActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView textView = new TextView(this);
        textView.setText("你的手机不支持使用ratel！\n" +
                "ratel主页：https://gitee.com/virjar/ratel\n" +
                "开源不易，可以的话，来这里请我喝杯咖啡");
        setContentView(textView);
    }
}
