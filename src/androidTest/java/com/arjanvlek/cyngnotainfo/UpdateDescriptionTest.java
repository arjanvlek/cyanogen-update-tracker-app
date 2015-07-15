package com.arjanvlek.cyngnotainfo;

import android.test.ActivityInstrumentationTestCase2;
import android.widget.Button;
import android.widget.TextView;

import com.arjanvlek.cyngnotainfo.views.UpdateDescriptionActivity;
import com.robotium.solo.Solo;

import java.util.Locale;

public class UpdateDescriptionTest extends ActivityInstrumentationTestCase2<MainActivity>{
    private Solo solo;

    public UpdateDescriptionTest() {
        super(MainActivity.class);
    }

    @Override
    public void setUp() throws Exception{
        solo = new Solo(getInstrumentation(), getActivity());
    }

    @Override
    public void tearDown() throws Exception{
        solo.finishOpenedActivities();
    }

    public void testUpdateDescription() throws Exception{
        Button updateDescriptionButton = (Button)solo.getView(R.id.updateDescriptionButton);
        solo.clickOnView(updateDescriptionButton);
        solo.waitForActivity(UpdateDescriptionActivity.class);
        String updateDescription = "This update is just a test!";
        TextView updateDescriptionTextView = (TextView)solo.getView(R.id.textView_update_details);
        assertEquals(updateDescription, updateDescriptionTextView.getText());
    }
}