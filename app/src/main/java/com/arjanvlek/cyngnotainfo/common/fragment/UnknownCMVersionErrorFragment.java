package com.arjanvlek.cyngnotainfo.common.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.arjanvlek.cyngnotainfo.R;

public class UnknownCMVersionErrorFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        //Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_unknowncmversionerror, container, false);
    }
}
