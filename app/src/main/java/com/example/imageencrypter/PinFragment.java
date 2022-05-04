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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONException;
import org.json.JSONObject;

public class PinFragment extends DialogFragment {
    public static String TAG = "PinDialog";
    private final static int NUM_OF_DIGITS = 4;
    EditText np1,np2,np3,np4, mobileNumber, oneTimePassword;
    LinearLayout codeLayout, mobielVerification;
    private Button button, verifyButton;
    private String androidId;
    Context applicationContext;
    private FirebaseFirestore fireStore = FirebaseFirestore.getInstance();
    private TextView title;
    private Boolean otpSent = false;
    private JSONObject otpResponse;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        applicationContext = getActivity().getApplicationContext();
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_pin, container, false);
        /** Getting the andorid UNIQUE ID **/
        this.androidId = Settings.Secure.getString(applicationContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        this.button = root.findViewById(R.id.proceed);
        verifyButton = root.findViewById(R.id.verifyButton);
        codeLayout = root.findViewById(R.id.codeLayout);
        mobielVerification = root.findViewById(R.id.mobielVerification);
        np1 = root.findViewById(R.id.digit1);
        np2 = root.findViewById(R.id.digit2);
        np3 = root.findViewById(R.id.digit3);
        np4 = root.findViewById(R.id.digit4);
        title = root.findViewById(R.id.pinTitle);
        mobileNumber = root.findViewById(R.id.mobileNumber);
        oneTimePassword = root.findViewById(R.id.oneTimePassword);
        boolean isRegisteration = getArguments().getBoolean("isRegisteration");
        String number = getArguments().getString("mobileNumber");
        if(isRegisteration){
            title.setText("PIN Setup");
            mobielVerification.setVisibility(View.VISIBLE);
            codeLayout.setVisibility(View.INVISIBLE);
            button.setVisibility(View.INVISIBLE);
            if(number!=null){
                mobileNumber.setText(number);
                mobileNumber.setEnabled(false);
            }
        }
        this.verifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(otpSent){
                    verifyOTP();
                }else{
                    requestForOTP();
                }
            }
        });
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

    private void verifyOTP(){
        int otp = new Integer(String.valueOf(oneTimePassword.getText()));
        String sessionId = null;
        try {
            sessionId = otpResponse.getString("Details");
            Toast.makeText(applicationContext,sessionId, Toast.LENGTH_LONG).show();
        }catch (JSONException e){
        }
        Toast.makeText(applicationContext, otp+sessionId, Toast.LENGTH_SHORT).show();
        String url = "https://2factor.in/API/V1/"+Conts.APIKEY+"/SMS/VERIFY/"+sessionId+"/"+otp;
        RequestQueue queue = Volley.newRequestQueue(applicationContext);
        JsonObjectRequest request = new JsonObjectRequest(url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        // Display the first 500 characters of the response string.
                        if(response!=null){
                            verifyButton.setVisibility(View.INVISIBLE);
                            codeLayout.setVisibility(View.VISIBLE);
                            button.setVisibility(View.VISIBLE);
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        queue.add(request);
    }

    private void requestForOTP(){
        String number = String.valueOf(mobileNumber.getText());
        String url = "https://2factor.in/API/V1/"+Conts.APIKEY+"/SMS/+91"+number+"/AUTOGEN";
        RequestQueue queue = Volley.newRequestQueue(applicationContext);
        JsonObjectRequest request = new JsonObjectRequest(url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        // Display the first 500 characters of the response string.
                        if(response!=null){
                            otpResponse = response;
                            otpSent = true;
                            oneTimePassword.setVisibility(View.VISIBLE);
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        queue.add(request);
    }

    private void setPin(){
        String number = String.valueOf(mobileNumber.getText());
        int result = new Integer(String.valueOf(np1.getText()))*1000;
        result = result+ new Integer(String.valueOf(np2.getText()))*100;
        result = result+ new Integer(String.valueOf(np3.getText()))*10;
        result = result+ new Integer(String.valueOf(np4.getText()));
        if(result>999){
            Intent i = new Intent().putExtra("resultPin", result);
            i.putExtra("mobileNumber", number);
            getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, i);
            dismiss();
        }
    }
}