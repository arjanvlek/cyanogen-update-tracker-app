package com.arjanvlek.cyngnotainfo.views;

import android.graphics.Bitmap;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.arjanvlek.cyngnotainfo.ApplicationContext;
import com.arjanvlek.cyngnotainfo.Model.InstallGuideData;
import com.arjanvlek.cyngnotainfo.R;
import com.arjanvlek.cyngnotainfo.Support.InstallGuideNetworkListener;
import com.arjanvlek.cyngnotainfo.Support.InstallGuideServerConnector;
import com.arjanvlek.cyngnotainfo.Support.ServerConnector;
import com.arjanvlek.cyngnotainfo.Support.SettingsManager;

import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_DEVICE_ID;
import static com.arjanvlek.cyngnotainfo.Support.SettingsManager.PROPERTY_UPDATE_METHOD_ID;

public class UpdateInstallationGuideActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_installation_instructions);

        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        ViewPager mViewPager = (ViewPager) findViewById(R.id.updateInstallationInstructionsPager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

    }
    
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a InstallationGuideFragment (defined as a static inner class below).
            return InstallationGuideFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 5 total pages.
            return 5;
        }
    }

    public static class InstallationGuideFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static InstallationGuideFragment newInstance(int sectionNumber) {
            InstallationGuideFragment fragment = new InstallationGuideFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            Bundle args = getArguments();
            final int sectionNumber = args.getInt(ARG_SECTION_NUMBER, 0);
            final SettingsManager settingsManager = new SettingsManager(getContext());

            final InstallGuideServerConnector serverConnector = new InstallGuideServerConnector();
            serverConnector.addNetworkListener(new InstallGuideNetworkListener() {
                @Override
                public void onInstallGuideContentsReceived(InstallGuideData contents) {
                    // view.setText(contents.getTextEn():
                }

                @Override
                public void onInstallGuideImageReceived(Bitmap image) {
                    // imageview.setImageBitmap(image);
                }
            });
            new Thread(new Runnable() {
                @Override
                public void run() {
                    serverConnector.fetchInstallGuidePageFromServer(sectionNumber, settingsManager.getLongPreference(PROPERTY_DEVICE_ID), settingsManager.getLongPreference(PROPERTY_UPDATE_METHOD_ID));
                }
            }).start();


            if (sectionNumber == 1) {
                return inflater.inflate(R.layout.fragment_update_installation_instructions_1, container, false);
            } else if (sectionNumber == 2) {
                return inflater.inflate(R.layout.fragment_update_installation_instructions_2, container, false);

            } else if (sectionNumber == 3) {
                return inflater.inflate(R.layout.fragment_update_installation_instructions_3, container, false);

            } else if (sectionNumber == 4) {
                return inflater.inflate(R.layout.fragment_update_installation_instructions_4, container, false);

            } else if (sectionNumber == 5) {
                return inflater.inflate(R.layout.fragment_update_installation_instructions_5, container, false);

            }
            return null;
        }
    }

    public void closeTutorial(View view) {
        finish();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
