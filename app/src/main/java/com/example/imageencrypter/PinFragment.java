package com.example.imageencrypter;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.google.firebase.firestore.FirebaseFirestore;

public class PinFragment extends DialogFragment {
    public static String TAG = "PinDialog";
    private final static int NUM_OF_DIGITS = 4;
    EditText np1,np2,np3,np4;
    private Button button;
    private String androidId;
    Context applicationContext;
    private FirebaseFirestore fireStore = FirebaseFirestore.getInstance();
    private TextView title;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        applicationContext = getActivity().getApplicationContext();
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_pin, container, false);
        /** Getting the andorid UNIQUE ID **/
        this.androidId = Settings.Secure.getString(applicationContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        this.button = root.findViewById(R.id.proceed);
        np1 = root.findViewById(R.id.digit1);
        np2 = root.findViewById(R.id.digit2);
        np3 = root.findViewById(R.id.digit3);
        np4 = root.findViewById(R.id.digit4);
        title = root.findViewById(R.id.pinTitle);
        boolean isRegisteration = getArguments().getBoolean("isRegisteration");
        if(isRegisteration)
            title.setText("PIN Setup");
        this.button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPin();
            }
        });
        np1.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(!s.toString().equals("")){
                    np2.requestFocus();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        np2.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(!s.toString().equals("")){
                    np3.requestFocus();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        np3.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(!s.toString().equals("")){
                    np4.requestFocus();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        np4.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(!s.toString().equals("")){
                    button.setEnabled(true);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        return root;
    }

    private void setPin(){
        int result = new Integer(String.valueOf(np1.getText()))*1000;
        result = result+ new Integer(String.valueOf(np2.getText()))*100;
        result = result+ new Integer(String.valueOf(np3.getText()))*10;
        result = result+ new Integer(String.valueOf(np4.getText()));
        if(result>999){
            Intent i = new Intent().putExtra("resultPin", result);
            getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, i);
            dismiss();
        }
    }
}