package com.arjanvlek.cyngnotainfo;

import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.arjanvlek.cyngnotainfo.views.HelpActivity;
import com.robotium.solo.Solo;


public class HelpTest extends ActivityInstrumentationTestCase2<HelpActivity> {


    private Solo solo;

    public HelpTest() {
        super(HelpActivity.class);
    }



    @Override
    public void setUp() throws Exception{
        solo = new Solo(getInstrumentation(), getActivity());
    }

    @Override
    public void tearDown() throws Exception{
        solo.finishOpenedActivities();
    }

    public void testHelpScreen() throws Exception {

        TextView helpOverviewField = (TextView) solo.getView(R.id.tut1bText1);
        TextView helpTopField = (TextView) solo.getView(R.id.tut1bText2);
        TextView helpMiddleField = (TextView) solo.getView(R.id.tut1bText2a);
        TextView helpBottomField = (TextView) solo.getView(R.id.tut1bText2b);
        TextView helpUsedSymbolsField = (TextView) solo.getView(R.id.tut1bText2c);
        TextView helpDownloadSizeField = (TextView) solo.getView(R.id.tut1bText4);

        ImageView helpDownloadSizeImage = (ImageView) solo.getView(R.id.tut1bdownloadSizeImage);

        assertEquals(View.VISIBLE, helpOverviewField.getVisibility());
        assertEquals(View.VISIBLE, helpTopField.getVisibility());
        assertEquals(View.VISIBLE, helpMiddleField.getVisibility());
        assertEquals(View.VISIBLE, helpBottomField.getVisibility());
        assertEquals(View.VISIBLE, helpUsedSymbolsField.getVisibility());
        assertEquals(View.VISIBLE, helpDownloadSizeField.getVisibility());

        assertEquals(View.VISIBLE, helpDownloadSizeImage.getVisibility());

        // Compare text
        HelpActivity activity = getActivity();
        assertEquals(activity.getString(R.string.help_how_works_app), helpOverviewField.getText());
        assertEquals(activity.getString(R.string.help_top_update), helpTopField.getText());
        assertEquals(activity.getString(R.string.help_middle_stats), helpMiddleField.getText());
        assertEquals(activity.getString(R.string.help_bottom_description), helpBottomField.getText());
        assertEquals(activity.getString(R.string.help_used_symbols), helpUsedSymbolsField.getText());
        assertEquals(activity.getString(R.string.help_symbol_download), helpDownloadSizeField.getText());
        assertEquals(activity.getString(R.string.icon), helpDownloadSizeImage.getContentDescription());


    }
}