package com.example.imageencrypter;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.File;

public class ImageFragment extends DialogFragment {

    public static String TAG = "ImageShowingDialog";
    private ImageView imageView;
    private File image = null;
    Context _context;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_image, container, false);
        this.image = new File(Environment.getExternalStorageDirectory()+"/ImageEncrypter/"+Conts.DECRYPTED_FILE);
        imageView = root.findViewById(R.id.decryptedImage);
        imageView.setImageBitmap(BitmapFactory.decodeFile(image.getAbsolutePath()));
        return root;
    }
    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        _context = context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.image.delete();
    }
}