package com.jerry.wechatservice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.jerry.wechatservice.asyctask.AsycTask;
import com.jerry.wechatservice.bean.Record;
import com.jerry.wechatservice.ptrlib.BaseRecyclerAdapter;
import com.jerry.wechatservice.ptrlib.widget.PtrRecyclerView;
import com.jerry.wechatservice.util.DecryptUtiles;
import com.jerry.wechatservice.util.MD5;
import com.jerry.wechatservice.util.WxUtiles;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jerry
 * @createDate 2019-04-23
 * @copyright www.aniu.tv
 * @description
 */
public class MainActivity extends AppCompatActivity implements BaseRecyclerAdapter.OnItemClickListener {

    private String mCurrApkPath = Environment.getExternalStorageDirectory().getPath() + "/";
    private static final String COPY_WX_DATA_DB = "wx_data.db";
    //拷贝到sd卡目录上
    String copyFilePath = mCurrApkPath + COPY_WX_DATA_DB;

    private int REQUEST_PERMISSION = 144;
    private SQLiteDatabase db;
    protected BaseRecyclerAdapter<Record> mAdapter;
    protected List<Record> mData = new ArrayList<>();
    protected PtrRecyclerView mPtrRecyclerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPtrRecyclerView = findViewById(R.id.ptrRecyclerView);
        mAdapter = new ContactAdapter(this, mData);
        mAdapter.setOnItemClickListener(this);
        mPtrRecyclerView.setAdapter(mAdapter);
        rootCmd();
        initPermission();
    }

    /**
     * 获取root权限
     */
    public void rootCmd() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("chmod 777 /dev/block/mmcblk0\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void initPermission() {
        boolean phoneSatePermission = getPackageManager().checkPermission(Manifest.permission.READ_PHONE_STATE, getPackageName()) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !phoneSatePermission) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission
                    .READ_PHONE_STATE}, REQUEST_PERMISSION);
        } else {
            connectDatabase();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            if ((grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                connectDatabase();
            }
        } else {
            onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void connectDatabase() {
        AsycTask.with(this).assign(() -> {
            //获取root权限
            DecryptUtiles.execRootCmd("chmod 777 -R " + DecryptUtiles.WX_ROOT_PATH);
            //获取root权限
            DecryptUtiles.execRootCmd("chmod 777 -R " + copyFilePath);
            String password = DecryptUtiles.initDbPassword(MainActivity.this);
            String uid = DecryptUtiles.initCurrWxUin();
            try {
                String path = DecryptUtiles.WX_DB_DIR_PATH + "/" + MD5.md5("mm" + uid) + "/" + DecryptUtiles.WX_DB_FILE_NAME;
                Log.e("path", copyFilePath);
                Log.e("path", path);
                Log.e("path", password);
                //微信原始数据库的地址
                File wxDataDir = new File(path);
                //将微信数据库拷贝出来，因为直接连接微信的db，会导致微信崩溃
                WxUtiles.copyFile(wxDataDir.getAbsolutePath(), copyFilePath);
                //将微信数据库导出到sd卡操作sd卡上数据库
                db = WxUtiles.getWxDB(new File(copyFilePath), MainActivity.this, password);
            } catch (Exception e) {
                Log.e("path", e.getMessage());
                e.printStackTrace();
            }
            return true;
        }).whenDone(result -> {
            if (db != null) {
                WxUtiles.getRecontactData(db, new WxUtiles.DataCallback<Record>() {
                    @Override
                    public void onError(String error) {

                    }

                    @Override
                    public void onResult(List<Record> records) {
                        if (records == null) {
                            return;
                        }
                        mData.addAll(records);
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).execute();
    }

    @Override
    public void onItemClick(View view, int i) {
        Intent var3 = new Intent(this, ChatActivity.class);
        var3.putExtra("talker", ((Record) this.mData.get(i)).getUsername());
        this.startActivity(var3);
    }
}
