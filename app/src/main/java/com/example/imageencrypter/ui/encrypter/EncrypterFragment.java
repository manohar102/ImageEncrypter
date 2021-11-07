package com.example.imageencrypter.ui.encrypter;

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

import com.example.imageencrypter.Encrypter;
import com.example.imageencrypter.R;
import com.example.imageencrypter.databinding.FragmentHomeBinding;
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

    private static final String TAG = "ImageEncrypter";
    private Button btnSelect,btnencrypt,btndecrypt;
    private ImageView imageView;

    private final static String ALGO_RANDOM_NUM_GENERATOR = "SHA1PRNG";
    private final static String ALGO_SECRET_KEY_GENERATOR = "AES";
    private final static int IV_LENGTH = 16;
    private final static String USERS = "users";
    private final static String ANDROID_ID = "ANDROID_ID";
    private final static String PUBLIC_KEY = "PUBLIC_KEY";
    private final static String IV = "IV";
    private final static String ENCRPTED_FILE = "/enc_image.swf";

    private File mydir;
    private Uri filePath;
    private final int PICK_IMAGE_REQUEST = 22;

    /* Unique Details We Store */
    private String m_androidId = null;
    private SecretKey publicKey = null;
    private byte[] iv = null;
    /* ----------------------- */

    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef = storage.getReference();
    FirebaseFirestore fireStore = FirebaseFirestore.getInstance();

    Context applicationContext = null;
    private FragmentHomeBinding binding;
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        applicationContext = getActivity().getApplicationContext();
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        mydir = new File(Environment.getExternalStorageDirectory()+"/ImageEncrypter");
        if(!mydir.exists()) {
            if(mydir.mkdirs()) {
                Toast.makeText(getActivity(),"Directory created" + this.mydir,
                        Toast.LENGTH_LONG).show();
            }
            else {
                Toast.makeText(getActivity(),"Failed to create Directory", Toast.LENGTH_LONG).show();
            }
        }
        m_androidId = Settings.Secure.getString(applicationContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if(m_androidId!=null){
            fireStore.collection(USERS).document(m_androidId).get()
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                        @RequiresApi(api = Build.VERSION_CODES.O)
                        @Override
                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                            Toast.makeText(getActivity(), "Document Fetch completed", Toast.LENGTH_SHORT).show();
                            if (task.isSuccessful()) {
                                DocumentSnapshot document = task.getResult();
                                if(document.exists()){
                                    Toast.makeText(getActivity(), "Document Found", Toast.LENGTH_SHORT).show();
                                    Map<String, Object> data = document.getData();
                                    byte[] decodedKey = Base64.getDecoder().decode((String) data.get(PUBLIC_KEY));
                                    publicKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
                                    m_androidId = data.get(ANDROID_ID).toString();
                                    Blob blob = (Blob) data.get(IV);
                                    iv = blob.toBytes();
                                }
                                else{
                                    Toast.makeText(getActivity(), "Document Not Found", Toast.LENGTH_SHORT).show();
                                    generateKeys();
                                }
                            } else {
                                Toast.makeText(getActivity(), "Error getting documents", Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Error getting documents: ", task.getException());
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        }
        btnSelect = root.findViewById(R.id.btnChoose);
        imageView = root.findViewById(R.id.imgView);
        btnencrypt = root.findViewById(R.id.encPicture);
        btndecrypt= root.findViewById(R.id.decPicture);
        // get the Firebase storage reference
        //storage = FirebaseStorage.getInstance();
        //storageReference = storage.getReference();

        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                SelectImage();
            }
        });

        btnencrypt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                encryptImage();
            }
        });

//        btndecrypt.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v)
//            {
//                decryptImage();
//            }
//        });
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void generateKeys(){
        Toast.makeText(getActivity(), "Key Generation Initialized", Toast.LENGTH_LONG).show();
        /* Keys Generation */
        try {
            publicKey = KeyGenerator.getInstance(ALGO_SECRET_KEY_GENERATOR).generateKey();
            iv = new byte[IV_LENGTH];
            SecureRandom.getInstance(ALGO_RANDOM_NUM_GENERATOR).nextBytes(iv);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        /* Keys Generation Completed */
        Blob blob = Blob.fromBytes(iv);
        Map<String, Object> metaData = new HashMap<>();
//        List<Object> key = new ArrayList<>();
//        key.add(publicKey);
//        key.add(m_androidId);
        Log.i("Public Key", publicKey.toString());
        metaData.put(PUBLIC_KEY, Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        metaData.put(ANDROID_ID, m_androidId);
        metaData.put(IV, blob);
        DocumentReference documentReference = fireStore.collection(USERS).document(m_androidId);
        documentReference.set(metaData).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Toast.makeText(getActivity(), "Device registered", Toast.LENGTH_LONG).show();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
    private void SelectImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Image from here..."),
                PICK_IMAGE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            filePath = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        applicationContext.getContentResolver(),
                        filePath);
                imageView.setImageBitmap(bitmap);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void encryptImage()
    {
        if (filePath != null) {
            final ProgressDialog progressDialog = new ProgressDialog(getActivity());
            progressDialog.setTitle("Encrypting...");
            progressDialog.show();
            //prgDialog.setMessage("Uploading...");
//            File inFile = new File(filePath.toString());

            File outFile = new File(mydir+ENCRPTED_FILE);
            Toast.makeText(getActivity(), "Initiated Encryption", Toast.LENGTH_SHORT).show();
            try {
                byte[] keyData = this.publicKey.getEncoded();
                SecretKey key2 = new SecretKeySpec(keyData, 0, keyData.length, ALGO_SECRET_KEY_GENERATOR);
//                byte[] iv = new byte[IV_LENGTH];
//                SecureRandom.getInstance(ALGO_RANDOM_NUM_GENERATOR).nextBytes(iv);
                AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv);
                InputStream inputStream = applicationContext.getContentResolver().openInputStream(filePath);
                Encrypter.encrypt(this.publicKey, paramSpec, inputStream, new FileOutputStream(outFile));
            } catch (Exception e) {
                e.printStackTrace();
            }

            String fileName = getFileName(filePath);
            String realPath = getRealPathFromURI(filePath);
            Map<String, Object> details = new HashMap<>();
            if(realPath!=null){
                File selectedFile = new File(realPath);
                Date date = new Date(selectedFile.lastModified());
                String time = new SimpleDateFormat("HH:mm:ss").format(date);
                details.put("PATH", realPath);
                details.put("LAST_MODIFIED", date+time);
            }
            String generatedUUID = UUID.randomUUID().toString();
            details.put("NAME", fileName);
            details.put("ENCRYPTION_DATE", new Date());
            details.put("UUID",generatedUUID);

            DocumentReference documentReference = fireStore.document(USERS+"/"+this.m_androidId).collection("Images").document(fileName);
            documentReference.set(details).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void unused) {
                    StorageReference ref = storageRef.child("images/" + generatedUUID);
                    // Progress Listener for loading
                    ref.putFile(Uri.fromFile(outFile)).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            // prgDialog.setMessage("dismiss...");
                            progressDialog.dismiss();
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