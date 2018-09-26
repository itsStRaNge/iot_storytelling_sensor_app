package edu.ntnu.iot_storytelling_sensor;

import android.Manifest;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import edu.ntnu.iot_storytelling_sensor.Network.FirebaseManager;
import edu.ntnu.iot_storytelling_sensor.Network.TCPInterface;
import edu.ntnu.iot_storytelling_sensor.Network.TCPTask;
import pl.droidsonroids.gif.GifImageView;


public class MainActivity extends AppCompatActivity implements View.OnDragListener,
                                                                TCPInterface,
                                                                View.OnTouchListener,
                                                                SensorEventListener {
    public final static int QR_Call = 0;
    public final static int PERMISSION_REQUEST_CAMERA = 1;

    private GifImageView m_field_obj;
    private GifImageView m_rel_obj;

    private int m_num_down_finish=0;
    private boolean DATA_SYNCED=false;
    private MediaPlayer m_mediaPlayer;

    public final static double TILT_THRESHOLD = 1.0;
    public final static float TILT_FACTOR = 1.5f;

    private LinearLayout m_topleft;
    private LinearLayout m_topright;
    private LinearLayout m_botleft;
    private LinearLayout m_botright;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        m_mediaPlayer = new MediaPlayer();

        //declaring Sensor Manager and sensor type
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        /* Check for permissions */
        check_camera_permission();

        /* Drag and Drop Init */
        m_topleft = findViewById(R.id.field_topleft);
        m_topleft.setOnDragListener(this);
        m_topright = findViewById(R.id.field_topright);
        m_topright.setOnDragListener(this);
        m_botleft = findViewById(R.id.field_bottomleft);
        m_botleft.setOnDragListener(this);
        m_botright = findViewById(R.id.field_bottomright);
        m_botright.setOnDragListener(this);
        findViewById(R.id.parent_view).setOnDragListener(this);

        m_field_obj = (GifImageView) findViewById(R.id.myimage_fields);
        m_field_obj.setOnTouchListener(this);
        m_rel_obj = (GifImageView) findViewById(R.id.myimage_rel);
        m_rel_obj.setOnTouchListener(this);

        ImageView camButton = (ImageView) findViewById(R.id.camera_button);
        camButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, QRScanner.class);
                startActivityForResult(intent, QR_Call);
            }
        });

        // Start Firebase Services
        new FirebaseManager(this);
    }

    /* PERMISSION REQUEST FOR CAMERS */
    private void check_camera_permission(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},PERMISSION_REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (!(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    check_camera_permission();
                }
            }
        }
    }
    /* ON TOUCH LISTENER */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            ClipData data = ClipData.newPlainText("", "");
            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(
                    v);
            v.startDrag(data, shadowBuilder, v, 0);
            v.setVisibility(View.INVISIBLE);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        Drawable enterShape = getDrawable(R.drawable.shape_droptarget);
        Drawable normalShape = getDrawable(R.drawable.shape);

        switch(event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED:
                if(v.getId() != R.id.parent_view)
                    v.setBackground(enterShape);
                break;
            case DragEvent.ACTION_DRAG_EXITED:
                if(v.getId() != R.id.parent_view)
                    v.setBackground(normalShape);
                break;
            case DragEvent.ACTION_DROP:
                ViewGroup container = (ViewGroup) v;

                if(container.getId() == R.id.parent_view){
                    float x_cord = event.getX() - m_rel_obj.getWidth() / 2;
                    float y_cord = event.getY() - m_rel_obj.getHeight() / 2;
                    m_rel_obj.setX(x_cord);
                    m_rel_obj.setY(y_cord);
                    m_rel_obj.setVisibility(View.VISIBLE);
                    m_rel_obj.bringToFront();
                    m_field_obj.setVisibility(View.INVISIBLE);
                }else{
                    ViewGroup owner = (ViewGroup) m_field_obj.getParent();
                    owner.removeView(m_field_obj);
                    container.addView(m_field_obj);
                    v.setBackground(normalShape);
                    m_field_obj.setVisibility(View.VISIBLE);
                    m_rel_obj.setVisibility(View.INVISIBLE);
                }
                create_request();
                break;
        }
        return true;
    }

    /* QR CODE SCANNER CALLBACK*/
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == QR_Call) {
            if (resultCode == RESULT_OK) {
                String qr_msg = data.getStringExtra("scanCode");
                Log.d("QR_CODE", qr_msg);
                //create_request(qr_msg);
            } else {
               Log.d("Error", "Could not read QR Code");
            }
        }
    }

    /* NETWORKING */
    private void create_request(){
        create_request("");
    }

    private void create_request(String qr_code){
        int position = 0; // stays zero if m_rel_obj is active

        if(m_field_obj.getVisibility() == View.VISIBLE){
            ViewGroup parent = (ViewGroup) m_field_obj.getParent();
            switch(parent.getId()){
                case R.id.field_topleft:
                    position = 1;
                    break;
                case R.id.field_topright:
                    position = 2;
                    break;
                case R.id.field_bottomleft:
                    position = 3;
                    break;
                case R.id.field_bottomright:
                    position = 4;
                    break;
            }
        }

        try {
            JSONObject json_pkg = new JSONObject();

            json_pkg.put("position", position);

            if(!qr_code.isEmpty())
                json_pkg.put("qr_code", qr_code);

            startRequest(json_pkg);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /* TCPTask CALLBACKS */
    @Override
    public void startRequest(JSONObject packet) {
        if(!DATA_SYNCED) return;

        TCPTask network = new TCPTask(this);
        network.send(packet);
    }

    @Override
    public void serverResult(String result) {
        if(!result.isEmpty()){
            Toast toast = Toast.makeText(getApplicationContext(), "Error: " + result, Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    /* FIREBASE NETWORKING CALLBACKS*/
    public void playAudio(String file_name){
        if(!DATA_SYNCED) return;
        if(m_mediaPlayer.isPlaying()) return;
        try {
            File directory = this.getFilesDir();
            File file = new File(directory, file_name);
            m_mediaPlayer.reset();
            m_mediaPlayer.setDataSource(file.getPath());
            m_mediaPlayer.prepare();
            m_mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void displayText(String file_name){
        if(!DATA_SYNCED) return;

        TextView text_view = findViewById(R.id.text_view);
        text_view.setText("");
        try {
            File directory = this.getFilesDir();
            File file = new File(directory, file_name);
            BufferedReader br = new BufferedReader(new FileReader(file));
            String st;
            while ((st = br.readLine()) != null)
                text_view.append(st);
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    public void showImage(String file_name){
        if(!DATA_SYNCED) return;

        File directory = this.getFilesDir();
        File file = new File(directory, file_name);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
        //((ImageView) findViewById(R.id.image_view)).setImageBitmap(bitmap);
    }

    public void deleteCache() {
        try {
            File dir = getCacheDir();
            deleteDir(dir);
            DATA_SYNCED = false;
        } catch (Exception e) { e.printStackTrace();}
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (String aChildren : children) {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else
            return dir != null && dir.isFile() && dir.delete();
    }

    public void download_finished(Boolean success) {
        if (success){
            m_num_down_finish++;

            if(m_num_down_finish >=3){
                m_num_down_finish=0;
                findViewById(R.id.download_progress_bar).setVisibility(View.INVISIBLE);
                DATA_SYNCED = true;
                Toast.makeText(getApplicationContext(), "Data sync complete!",
                        Toast.LENGTH_LONG).show();
            }
        }else{
            Toast.makeText(getApplicationContext(), "Failed to Download Components",
                    Toast.LENGTH_LONG).show();
        }
    }

    /* TILT SENSOR CALLBACKS */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //http://www.devexchanges.info/2015/05/detecting-tilt-device-by-using-sensor.html
        if(m_rel_obj.getVisibility() != View.VISIBLE) return;

        float x = sensorEvent.values[0] * TILT_FACTOR;
        if (x < -TILT_THRESHOLD || TILT_THRESHOLD < x) {
            float pos_x = m_rel_obj.getX();
            m_rel_obj.setX(pos_x - x);
        }

        float y = sensorEvent.values[1] * TILT_FACTOR;
        if (y < -TILT_THRESHOLD || TILT_THRESHOLD < y) {
            float pos_y = m_rel_obj.getY();
            m_rel_obj.setY(pos_y - y);
        }
        check_collision();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    private void check_collision(){
        Rect buf_obj = new Rect();
        m_rel_obj.getHitRect(buf_obj);
        Rect buf_field = new Rect();

        m_topleft.getHitRect(buf_field);
        Log.d("COLLISION", "FIELD " + buf_field.toString() + "\nOBJECT " + buf_obj.toString());
        if(Rect.intersects(buf_obj, buf_field)){
            set_obj_in_new_field(m_topleft);
            return;
        }

        m_topright.getHitRect(buf_field);
        if(Rect.intersects(buf_obj, buf_field)){
            set_obj_in_new_field(m_topright);
            return;
        }

        m_botleft.getHitRect(buf_field);
        if(Rect.intersects(buf_obj, buf_field)){
            set_obj_in_new_field(m_botleft);
            return;
        }

        m_botright.getHitRect(buf_field);
        if(Rect.intersects(buf_obj, buf_field)){
            set_obj_in_new_field(m_botright);
        }
    }

    private void set_obj_in_new_field(LinearLayout new_field){
        ViewGroup owner = (ViewGroup) m_field_obj.getParent();
        owner.removeView(m_field_obj);
        new_field.addView(m_field_obj);
        m_field_obj.setVisibility(View.VISIBLE);
        m_rel_obj.setVisibility(View.INVISIBLE);
    }
}
