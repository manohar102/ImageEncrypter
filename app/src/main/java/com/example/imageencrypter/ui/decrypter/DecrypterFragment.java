package com.example.imageencrypter.ui.decrypter;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.example.imageencrypter.Conts;
import com.example.imageencrypter.Encrypter;
import com.example.imageencrypter.ImageFragment;
import com.example.imageencrypter.PinFragment;
import com.example.imageencrypter.R;
import com.example.imageencrypter.databinding.FragmentDecrypterBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static android.app.Activity.RESULT_OK;

public class DecrypterFragment extends Fragment {

    private static final int PIN_FRAGMENT = 888;
    private static final String TAG = "ImageDecrypter";

    /** Components **/
    ListView listView = null;

    /** Unique Details **/
    private String m_androidId = null;
    private SecretKey publicKey = null;
    private byte[] iv = null;
    private int userPin;

    /** Global Variables **/
    private File mydir;
    Context applicationContext = null;
    private List<String> imageArray = new ArrayList<>();
    private ArrayList<Map<String, Object>> imageData = new ArrayList<>();
    private FragmentDecrypterBinding binding;
    private int selectedImage;

    /** fire store **/
    private FirebaseStorage storage = FirebaseStorage.getInstance();
    private StorageReference storageRef = storage.getReference();
    private FirebaseFirestore fireStore = FirebaseFirestore.getInstance();



    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        this.applicationContext = getActivity().getApplicationContext();
        this.mydir = new File(Environment.getExternalStorageDirectory()+"/ImageEncrypter");
        this.binding = FragmentDecrypterBinding.inflate(inflater, container, false);
        View root = this.binding.getRoot();
        this.listView = root.findViewById(R.id.listView);
        this.m_androidId = Settings.Secure.getString(applicationContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if(this.m_androidId!=null){
            fireStore.collection(Conts.USERS).document(this.m_androidId).get()
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
                                    userPin = ((Number)data.get(Conts.PIN)).intValue();
                                    Blob blob = (Blob) data.get(Conts.IV);
                                    iv = blob.toBytes();
                                }
                                else{
                                    Toast.makeText(applicationContext, "Account Not Found", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(applicationContext, "Error getting documents", Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Error getting documents: ", task.getException());
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            fireStore.collection(Conts.USERS).document(this.m_androidId).collection(Conts.IMAGES).get()
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @RequiresApi(api = Build.VERSION_CODES.O)
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if (task.isSuccessful()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    if (document.exists()) {
                                         processData(document.getData());
                                    } else {
                                        Toast.makeText(applicationContext, "No Encrypted Images found", Toast.LENGTH_SHORT).show();
                                    }
                                }
                                ArrayAdapter adapter = new ArrayAdapter<String>(getActivity().getApplicationContext(),R.layout.activity_listview,R.id.textView,imageArray);
                                listView.setAdapter(adapter);
                            } else {
                                Toast.makeText(applicationContext, "Error getting documents", Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Error getting documents: ", task.getException());
                            }
                        }
                    }).addOnFailureListener(e -> Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
        }
        listView.setOnItemClickListener((listView, itemView, itemPosition, itemId) -> {this.selectedImage = itemPosition; this.openPinFragment();});
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
    }

    public void openPinFragment(){
        PinFragment pinFragment = new PinFragment();
        pinFragment.setTargetFragment(this, PIN_FRAGMENT);
        pinFragment.show(getParentFragmentManager(), ImageFragment.TAG);
    }

    public void processData(Map<String, Object> data){
        this.imageData.add(data);
        this.imageArray.add((String) data.get(Conts.NAME));
    }

    private void decryptImage(Map<String, Object> imageData) {

        final ProgressDialog progDialog = new ProgressDialog(getActivity());
        progDialog.setTitle("Decrypting...");
        progDialog.show();
        StorageReference gsReference = storage.getReferenceFromUrl("gs://imageencrypter.appspot.com/"+Conts.STORAGE+"/"+imageData.get(Conts.UUID));

        final long ONE_MEGABYTE = 9000000;
        gsReference.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                File outFile = new File(mydir+Conts.ENCRPTED_FILE);
                try (FileOutputStream fos = new FileOutputStream(mydir+Conts.ENCRPTED_FILE)) {
                    fos.write(bytes);
                    //fos.close(); There is no more need for this line since you had created the instance of "fos" inside the try. And this will automatically close the OutputStream
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    File outFile_dec = new File(mydir+Conts.DECRYPTED_FILE);
                    byte[] keyData = publicKey.getEncoded();
                    SecretKey key2 = new SecretKeySpec(keyData, 0, keyData.length, Conts.ALGO_SECRET_KEY_GENERATOR);
                    AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv);
                    Encrypter.decrypt(key2, paramSpec, new FileInputStream(outFile), new FileOutputStream(outFile_dec));
                    progDialog.dismiss();
                    outFile.delete();
                    new ImageFragment().show(getChildFragmentManager(), ImageFragment.TAG);
                    Toast.makeText(applicationContext, "Image Decrypted!!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // Handle any errors
                // Error, Image not uploaded
                progDialog.dismiss();
                Toast.makeText(getActivity(),"Failed " + e.getMessage(),Toast.LENGTH_SHORT).show();
            }
        });
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PIN_FRAGMENT && resultCode == Activity.RESULT_OK
                && data != null) {
            Bundle bundle = data.getExtras();
            int pin = bundle.getInt("resultPin");
            if(pin>999){
                if(this.userPin == pin){
                    this.decryptImage(this.imageData.get(this.selectedImage));
                }
                else{
                    Toast.makeText(applicationContext, "Incorrect PIN, Please try again..!", Toast.LENGTH_LONG).show();
                    this.openPinFragment();
                }
            }
            else{
                Toast.makeText(applicationContext, "Please Setup Valid PIN", Toast.LENGTH_LONG).show();
                this.openPinFragment();
            }
        }
        else if (resultCode == Activity.RESULT_CANCELED) {
            System.exit(0);
        }
    }
}