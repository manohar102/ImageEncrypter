package com.example.imageencrypter.ui.decrypter;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.example.imageencrypter.Encrypter;
import com.example.imageencrypter.R;
import com.example.imageencrypter.databinding.FragmentDashboardBinding;
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

public class DecrypterFragment extends Fragment {

    private static final String TAG = "ImageEncrypter";
    private FragmentDashboardBinding binding;

    private final static String ALGO_RANDOM_NUM_GENERATOR = "SHA1PRNG";
    private final static String ALGO_SECRET_KEY_GENERATOR = "AES";
    private final static int IV_LENGTH = 16;
    private final static String USERS = "users";
    private final static String ANDROID_ID = "ANDROID_ID";
    private final static String PUBLIC_KEY = "PUBLIC_KEY";
    private final static String IV = "IV";
    private final static String ENCRPTED_FILE = "/enc_image.swf";

    private File mydir;
    /* Unique Items we are retreiving */
    private String m_androidId = null;
    private SecretKey publicKey = null;
    private byte[] iv = null;
    /* --------------------------------- */

    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef = storage.getReference();
    FirebaseFirestore fireStore = FirebaseFirestore.getInstance();

    Context applicationContext = null;
    ListView listView = null;
    List<String> imageArray = new ArrayList<>();
    ArrayList<Map<String, Object>> imageData = new ArrayList<>();

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        applicationContext = getActivity().getApplicationContext();
        mydir = new File(Environment.getExternalStorageDirectory()+"/ImageEncrypter");
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        listView = root.findViewById(R.id.listView);
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
            fireStore.collection(USERS).document(m_androidId).collection("Images").get()
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @RequiresApi(api = Build.VERSION_CODES.O)
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            Toast.makeText(getActivity(), "Images Fetch completed", Toast.LENGTH_SHORT).show();
                            if (task.isSuccessful()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    if (document.exists()) {
                                        Toast.makeText(getActivity(), "Images Found", Toast.LENGTH_SHORT).show();
                                         processData(document.getData());
                                    } else {
                                        Toast.makeText(getActivity(), "Images Not found", Toast.LENGTH_SHORT).show();
                                    }
                                }
                                ArrayAdapter adapter = new ArrayAdapter<String>(getActivity().getApplicationContext(),R.layout.activity_listview,R.id.textView,imageArray);
                                listView.setAdapter(adapter);
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
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> listView, View itemView, int itemPosition, long itemId)
            {
                decryptImage(imageData.get(itemPosition));
            }
        });
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void processData(Map<String, Object> data){
        imageData.add(data);
        imageArray.add((String) data.get("NAME"));
    }

    private void decryptImage(Map<String, Object> imageData) {

        final ProgressDialog progDialog = new ProgressDialog(getActivity());
        progDialog.setTitle("Decrypting...");
        progDialog.show();

        // Create a storage reference from our app
        StorageReference storageRef = storage.getReference();
        StorageReference gsReference = storage.getReferenceFromUrl("gs://imageencrypter.appspot.com/images/"+imageData.get("UUID"));
        StorageReference islandRef = storageRef.child("images/test_5_result.png");

        final long ONE_MEGABYTE = 9000000;
        gsReference.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                // Data for "images/island.jpg" is returns, use this as needed
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                File outFile = new File(mydir+ENCRPTED_FILE);
                try (FileOutputStream fos = new FileOutputStream(mydir+ENCRPTED_FILE)) {
                    fos.write(bytes);
                    //fos.close(); There is no more need for this line since you had created the instance of "fos" inside the try. And this will automatically close the OutputStream
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
//                InputStream myInputStream = new ByteArrayInputStream(bytes);
                try {
                    File outFile_dec = new File(mydir+"/image.png");
                    byte[] keyData = publicKey.getEncoded();
                    SecretKey key2 = new SecretKeySpec(keyData, 0, keyData.length, ALGO_SECRET_KEY_GENERATOR);
//                    byte[] iv = new byte[IV_LENGTH];
//                    SecureRandom.getInstance(ALGO_RANDOM_NUM_GENERATOR).nextBytes(iv);
                    AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv);
                    Encrypter.decrypt(key2, paramSpec, new FileInputStream(outFile), new FileOutputStream(outFile_dec));
    //                imageView.setImageBitmap(bitmap);
                    progDialog.dismiss();
                    Toast.makeText(getActivity(), "Image Decrypted!!", Toast.LENGTH_SHORT).show();
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
}