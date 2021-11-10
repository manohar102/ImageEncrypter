package com.example.imageencrypter.ui.encrypter;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.example.imageencrypter.Conts;
import com.example.imageencrypter.Encrypter;
import com.example.imageencrypter.ImageFragment;
import com.example.imageencrypter.PinFragment;
import com.example.imageencrypter.R;
import com.example.imageencrypter.databinding.FragmentEncrypterBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static android.app.Activity.RESULT_OK;

public class EncrypterFragment extends Fragment {

    private static final int PIN_FRAGMENT = 888;
    private static final String TAG = "ImageEncrypter";
    /** Components **/
    private Button btnSelect,btnencrypt;
    private ImageView imageView;

    /** Unique Details We Store **/
    private String m_androidId = null;
    private SecretKey publicKey = null;
    private byte[] iv = null;

    /** Global Variables **/
    private File mydir;
    private Uri filePath;
    private final int PICK_IMAGE_REQUEST = 22;
    private FragmentEncrypterBinding binding;
    Context applicationContext = null;

    /** fire store **/
    private FirebaseStorage storage = FirebaseStorage.getInstance();
    private StorageReference storageRef = storage.getReference();
    private FirebaseFirestore fireStore = FirebaseFirestore.getInstance();


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        applicationContext = getActivity().getApplicationContext();
        this.binding = FragmentEncrypterBinding.inflate(inflater, container, false);
        View root = this.binding.getRoot();

        this.mydir = new File(Environment.getExternalStorageDirectory()+"/ImageEncrypter");
        if(!this.mydir.exists()) {
            if(this.mydir.mkdirs()) {
                Toast.makeText(applicationContext,"Directory created" + this.mydir, Toast.LENGTH_LONG).show();
            }
            else {
                Toast.makeText(applicationContext,"Failed to create Directory", Toast.LENGTH_LONG).show();
            }
        }
        /** Getting the andorid UNIQUE ID **/
        this.m_androidId = Settings.Secure.getString(applicationContext.getContentResolver(), Settings.Secure.ANDROID_ID);

        this.verifyRegistration();

        this.btnSelect = root.findViewById(R.id.btnChoose);
        this.imageView = root.findViewById(R.id.imgView);
        this.btnencrypt = root.findViewById(R.id.encPicture);

        this.btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                SelectImage();
            }
        });

        this.btnencrypt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                encryptImage();
            }
        });
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
    }

    private void verifyRegistration(){
        if(this.m_androidId!=null){
            fireStore.collection(Conts.USERS).document(m_androidId).get()
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                        @RequiresApi(api = Build.VERSION_CODES.O)
                        @Override
                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                            if (task.isSuccessful()) {
                                DocumentSnapshot document = task.getResult();
                                if(document.exists()){
                                    Map<String, Object> data = document.getData();
                                    byte[] decodedKey = Base64.getDecoder().decode((String) data.get(Conts.PUBLIC_KEY));
                                    publicKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
                                    m_androidId = data.get(Conts.ANDROID_ID).toString();
                                    Blob blob = (Blob) data.get(Conts.IV);
                                    iv = blob.toBytes();
                                }
                                else{
                                    Toast.makeText(applicationContext, "Account Not Found", Toast.LENGTH_SHORT).show();
                                    askPinSetup();
                                }
                            } else {
                                Toast.makeText(applicationContext, "Error getting documents", Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Error getting documents: ", task.getException());
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(applicationContext, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    private void askPinSetup(){
        PinFragment pinFragment = new PinFragment();
        pinFragment.setTargetFragment(this, PIN_FRAGMENT);
        pinFragment.show(getParentFragmentManager(), ImageFragment.TAG);
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void generateKeys(int pin){
        Toast.makeText(getActivity(), "Key Generation Initialized", Toast.LENGTH_LONG).show();
        /** Keys Generation **/
        try {
            this.publicKey = KeyGenerator.getInstance(Conts.ALGO_SECRET_KEY_GENERATOR).generateKey();
            this.iv = new byte[Conts.IV_LENGTH];
            SecureRandom.getInstance(Conts.ALGO_RANDOM_NUM_GENERATOR).nextBytes(iv);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        /** Keys Generation Completed **/
        /** Storing to FireStore **/
        Blob blob = Blob.fromBytes(iv);
        Map<String, Object> metaData = new HashMap<>();
        metaData.put(Conts.PUBLIC_KEY, Base64.getEncoder().encodeToString(this.publicKey.getEncoded()));
        metaData.put(Conts.ANDROID_ID, this.m_androidId);
        metaData.put(Conts.IV, blob);
        metaData.put(Conts.PIN, pin);
        DocumentReference documentReference = fireStore.collection(Conts.USERS).document(this.m_androidId);
        documentReference.set(metaData).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Toast.makeText(applicationContext, "Device registered", Toast.LENGTH_LONG).show();
            }
        }).addOnFailureListener(e -> Toast.makeText(applicationContext, e.getMessage(), Toast.LENGTH_LONG).show());
    }
    private void SelectImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Image from here..."),
                PICK_IMAGE_REQUEST);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            this.filePath = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        applicationContext.getContentResolver(),
                        this.filePath);
                this.imageView.setImageBitmap(bitmap);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if (requestCode == PIN_FRAGMENT && resultCode == Activity.RESULT_OK
                && data != null) {
            Bundle bundle = data.getExtras();
            int pin = bundle.getInt("resultPin");
            if(pin>999){
                this.generateKeys(pin);
            }
            else{
                Toast.makeText(applicationContext, "PIN Not Valid, Please Enter Valid PIN", Toast.LENGTH_LONG).show();
                this.askPinSetup();
            }
        }
        else if (resultCode == Activity.RESULT_CANCELED) {
            System.exit(0);
        }
    }

    private void encryptImage()
    {
        if (this.filePath != null) {
            final ProgressDialog progressDialog = new ProgressDialog(getActivity());
            progressDialog.setTitle("Encrypting...");
            progressDialog.show();

            File outFile = new File(this.mydir+Conts.ENCRPTED_FILE);
            Toast.makeText(getActivity(), "Initiated Encryption", Toast.LENGTH_SHORT).show();
            try {
                AlgorithmParameterSpec paramSpec = new IvParameterSpec(this.iv);
                InputStream inputStream = applicationContext.getContentResolver().openInputStream(this.filePath);
                Encrypter.encrypt(this.publicKey, paramSpec, inputStream, new FileOutputStream(outFile));
            } catch (Exception e) {
                e.printStackTrace();
            }

            String fileName = getFileName(this.filePath);
            String realPath = getRealPathFromURI(this.filePath);
            Map<String, Object> details = new HashMap<>();
            if(realPath!=null){
                File selectedFile = new File(realPath);
                Date date = new Date(selectedFile.lastModified());
                String time = new SimpleDateFormat("HH:mm:ss").format(date);
                details.put("PATH", realPath);
                details.put("LAST_MODIFIED", date+time);
            }
            String generatedUUID = UUID.randomUUID().toString();
            details.put(Conts.NAME, fileName);
            details.put(Conts.ENCRYPTION_DATE, new Date());
            details.put(Conts.UUID,generatedUUID);
            DocumentReference documentReference = fireStore.document(Conts.USERS+"/"+this.m_androidId+"/"+Conts.IMAGES+"/"+fileName);
            documentReference.set(details).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void unused) {
                    StorageReference ref = storageRef.child(Conts.STORAGE+"/" + generatedUUID);
                    ref.putFile(Uri.fromFile(outFile)).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            progressDialog.dismiss();
                            outFile.delete();
                            imageView.setImageBitmap(null);
                            Toast.makeText(getActivity(), "Image Encrypted!!", Toast.LENGTH_SHORT).show();
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            progressDialog.dismiss();
                            Toast.makeText(getActivity(), "Failed " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }).addOnProgressListener(taskSnapshot -> {
                        double progress = (100.0 * taskSnapshot.getBytesTransferred()
                                / taskSnapshot.getTotalByteCount());
                        progressDialog.setMessage("Encrypted " + (int)progress + "%");
                    });
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = applicationContext.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = applicationContext.getContentResolver().query(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
}