package com.example.imageencrypter.ui.account;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.example.imageencrypter.Conts;
import com.example.imageencrypter.ImageFragment;
import com.example.imageencrypter.PinFragment;
import com.example.imageencrypter.R;
import com.example.imageencrypter.databinding.FragmentAccountBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

public class AccountFragment extends Fragment {

    private static final int PIN_FRAGMENT = 888;

    private FragmentAccountBinding binding;
    Context applicationContext;
    private String m_androidId = null;
    private FirebaseFirestore fireStore = FirebaseFirestore.getInstance();
    TextView textView;
    Button forgotPassword;
    String mobileNumber;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        applicationContext = getActivity().getApplicationContext();
        binding = FragmentAccountBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        textView = root.findViewById(R.id.noOfImages);
        forgotPassword = root.findViewById(R.id.forgotPassword);
        this.m_androidId = Settings.Secure.getString(applicationContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        fireStore.collection(Conts.USERS).document(this.m_androidId).collection(Conts.IMAGES).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()){
                    int count = 0;
                    for (DocumentSnapshot document : task.getResult()) {
                        count++;
                    }
                    textView.setText(String.valueOf(count));
                }
            }
        });
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
                                    mobileNumber = data.get(Conts.MOBILE_NUMBER).toString();
                                }
                            } else {
                                Toast.makeText(applicationContext, "Error getting documents", Toast.LENGTH_LONG).show();
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(applicationContext, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
        forgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetPin();
            }
        });
        return root;
    }

    private void resetPin(){
        Bundle args = new Bundle();
        args.putBoolean("isRegisteration", true);
        args.putString("mobileNumber", mobileNumber);
        PinFragment pinFragment = new PinFragment();
        pinFragment.setArguments(args);
        pinFragment.setTargetFragment(this, PIN_FRAGMENT);
        pinFragment.show(getParentFragmentManager(), ImageFragment.TAG);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PIN_FRAGMENT && resultCode == Activity.RESULT_OK
                && data != null) {
            Bundle bundle = data.getExtras();
            int pin = bundle.getInt("resultPin");
            String mobileNumber = bundle.getString("mobileNumber");
            if(pin>999){
                updatePin(pin, mobileNumber);
            }
            else{
                Toast.makeText(applicationContext, "PIN Update failed...! Please try again", Toast.LENGTH_LONG).show();
                this.resetPin();
            }
        }
        else if (resultCode == Activity.RESULT_CANCELED) {
            System.exit(0);
        }
    }

    private void updatePin(int pin, String mobileNumber){
        Map<String, Object> metaData = new HashMap<>();
        metaData.put(Conts.ANDROID_ID, this.m_androidId);
        metaData.put(Conts.PIN, pin);
        metaData.put(Conts.MOBILE_NUMBER, mobileNumber);
        DocumentReference documentReference = fireStore.collection(Conts.USERS).document(this.m_androidId);
        documentReference.update(metaData).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Toast.makeText(applicationContext, "PIN Updated Successfully", Toast.LENGTH_LONG).show();
            }
        }).addOnFailureListener(e -> Toast.makeText(applicationContext, e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}