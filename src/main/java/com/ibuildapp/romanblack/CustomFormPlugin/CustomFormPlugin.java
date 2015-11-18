package com.ibuildapp.romanblack.CustomFormPlugin;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.*;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;
import com.appbuilder.sdk.android.AppBuilderModuleMain;
import com.appbuilder.sdk.android.Utils;
import com.appbuilder.sdk.android.Widget;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Main module class. Module entry point.
 * Represents custom form widget.
 */
public class CustomFormPlugin extends AppBuilderModuleMain { 

    private final int INITIALIZATION_FAILED = 0;
    private final int NEED_INTERNET_CONNECTION = 1;
    private final int LOADING_ABORTED = 2;
    private final int SEND_FAILED = 3;
    private final int SHOW_FORM = 4;
    private boolean useCache = false;
    private boolean isOnline = false;
    private String cacheMD5 = null;
    private String cachePath = null;
    private String widgetMD5 = null;
    private int lrPadding = 17;
    private String title = "";
    private Widget widget = null;
    private ProgressDialog progressDialog = null;
    private ArrayList<Form> forms = new ArrayList<Form>();
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case INITIALIZATION_FAILED: {
                    Toast.makeText(CustomFormPlugin.this,
                            R.string.alert_cannot_init,
                            Toast.LENGTH_LONG).show();
                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            finish();
                        }
                    }, 5000);
                }
                break;
                case NEED_INTERNET_CONNECTION: {
                    Toast.makeText(CustomFormPlugin.this, R.string.alert_no_internet,
                            Toast.LENGTH_LONG).show();
                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            finish();
                        }
                    }, 5000);
                }
                break;
                case LOADING_ABORTED: {
                    CustomFormPlugin.this.closeActivity();
                }
                break;
                case SEND_FAILED: {
                    Toast.makeText(CustomFormPlugin.this, R.string.alert_no_internet,
                            Toast.LENGTH_LONG).show();
                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                        }
                    }, 5000);
                }
                break;
                case SHOW_FORM: {
                    buildForm();
                }
                break;
            }
        }
    };

   @SuppressLint("ValidFragment")
    public class DialogDatePicker  implements DatePickerDialog.OnDateSetListener{

        private int year;
        private int month;
        private int day;
        private Button edit;
        private GroupItemCalendar ef;

        public DialogDatePicker(int startDay, int startMonth, int startYear, Button button, GroupItemCalendar ef)
        {
            year = startYear;
            day = startDay;
            month = startMonth;
            edit = button;
            this.ef = ef;
        }
    public Date getDate()
    {
        return new Date();
    }
        public void onDateSet(DatePicker view, int year, int monthOfYear,
                              int dayOfMonth) {
            // TODO Auto-generated method stub
            // Do something with the date chosen by the user
                try {
                this.year = year;
                month = monthOfYear;
                day = dayOfMonth;
                edit.setTextColor(Statics.color1);
                Date date = new Date(year-1900, monthOfYear, day);
                String datePattern = getResources().getString(R.string.data_picker_pattern);
                DateFormat DATE_FORMAT = new SimpleDateFormat(datePattern);
                String s = DATE_FORMAT.format(date);
                ef.setSet(true);
                ef.setDate(date);
                edit.setText(s);
            }
            catch(Throwable e)
            {
                e.printStackTrace();
            }


        }
    }


    @Override
    public void create() {
        try { 

            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setContentView(R.layout.romanblack_custom_form_main);
            setTitle(R.string.roamnblack_custom_form);

            Intent currentIntent = getIntent();
            Bundle store = currentIntent.getExtras();
            widget = (Widget) store.getSerializable("Widget");
            if (widget == null) {
                handler.sendEmptyMessageDelayed(INITIALIZATION_FAILED, 100);
                return;
            }

            String pluginXmlDataFilePath = store.getString("WidgetFile");


            if ( !TextUtils.isEmpty(pluginXmlDataFilePath) ) {
                StringBuffer fileData = null;
                try {
                    fileData = new StringBuffer();
                    BufferedReader reader = new BufferedReader(new FileReader(pluginXmlDataFilePath));
                    char[] buf = new char[1024];
                    int numRead;
                    while((numRead=reader.read(buf)) != -1){
                        String readData = String.valueOf(buf, 0, numRead);
                        fileData.append(readData);
                    }
                    reader.close();
                } catch(IOException ioe) {
                    handler.sendEmptyMessageDelayed(INITIALIZATION_FAILED, 100);
                    return;
                }

                widget.setNormalPluginXmlData(fileData.toString());
            }

            widgetMD5 = Utils.md5(widget.getPluginXmlData());

            if (widget.getTitle() != null && widget.getTitle().length() != 0) {
                setTopBarTitle(widget.getTitle());
            } else {
                setTopBarTitle(getResources().getString(R.string.roamnblack_custom_form));
            }

            if (widget.getPluginXmlData().length() == 0) {
                handler.sendEmptyMessageDelayed(INITIALIZATION_FAILED, 100);
                return;
            }
            // topbar initialization
            boolean showSideBar = ((Boolean) getIntent().getExtras().getSerializable("showSideBar")).booleanValue();
            if (!showSideBar) {
                setTopBarLeftButtonText(getResources().getString(R.string.common_home_upper), true, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        finish();
                    }
                });
            }
            cachePath = widget.getCachePath() + "/customform-" + widget.getOrder();
            File cache = new File(this.cachePath);
            if (!cache.exists()) {
                cache.mkdirs();
            }

            File cacheData = new File(cachePath + "/cache.data");

            if (cacheData.exists() && cacheData.length() > 0) {
                cacheMD5 = readFileToString(cachePath + "/cache.md5")
                        .replace("\n", "");
                if (cacheMD5.equals(widgetMD5)) {
                    useCache = true;
                } else {
                    File[] files = cache.listFiles();
                    for (int i = 0; i < files.length; i++) {
                        files[i].delete();
                    }
                    try {
                        BufferedWriter bw = new BufferedWriter(
                                new FileWriter(new File(cachePath + "/cache.md5")));
                        bw.write(widgetMD5);
                        bw.close();
                        Log.d("IMAGES PLUGIN CACHE MD5", "SUCCESS");
                    } catch (Exception e) {
                        Log.w("IMAGES PLUGIN CACHE MD5", e);
                    }
                }
            }

            ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnectedOrConnecting()) {
                isOnline = true;
            }

            if (!isOnline && !useCache) {
                handler.sendEmptyMessage(NEED_INTERNET_CONNECTION);
                return;
            }

            progressDialog = ProgressDialog.show(this, null, getString(R.string.common_loading_upper), true);
            progressDialog.setCancelable(true);
            progressDialog.setOnCancelListener(
                    new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface arg0) {
                    handler.sendEmptyMessage(LOADING_ABORTED);
                }
            });

            new Thread() {
                @Override
                public void run() {
                    try {//ErrorLogging

                        EntityParser parser = new EntityParser(widget.getPluginXmlData());
                        parser.parse();

                        title = widget.getTitle();
                        forms = parser.getForms();

                        Statics.color1 = parser.getColor1();
                        Statics.color2 = parser.getColor2();
                        Statics.color3 = parser.getColor3();
                        Statics.color4 = parser.getColor4();
                        Statics.color5 = parser.getColor5();

                        handler.sendEmptyMessage(SHOW_FORM);

                    } catch (Exception e) {
                    }
                }
            }.start();


        } catch (Exception e) {
        }
    }

    /**
     * Builds custom form depending on module configuration.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void buildForm() {
        try {

            View root = findViewById(R.id.romanblack_custom_form_main_root);
            root.setBackgroundColor(Statics.color1); 

            if (!forms.isEmpty()) {
                LinearLayout container =
                        (LinearLayout) findViewById(R.id.romanblack_custom_form_container);
                container.setBackgroundColor(Statics.color1);
                for (int j = 0; j < forms.get(0).getGroups().size(); j++) {
                    Group group = forms.get(0).getGroups().get(j);
                    final LinearLayout groupLL = new LinearLayout(this);
                    groupLL.setOrientation(LinearLayout.VERTICAL);
                    groupLL.setBackgroundColor(Statics.color1);
                    DisplayMetrics metrix = getResources().getDisplayMetrics();

                    if (!group.getTitle().equals("")) {
                        TextView textView = new TextView(this);

                        SpannableStringBuilder ssb =
                                new SpannableStringBuilder(group.getTitle());
                        StyleSpan ss = new StyleSpan(Typeface.NORMAL);

                        ssb.setSpan(ss, 0, ssb.length(),
                                Spanned.SPAN_EXCLUSIVE_INCLUSIVE);

                        textView.setText(ssb);
                        textView.setTextColor(Statics.color5);
                        textView.setTextSize(20);

                        if (forms.get(0).getGroups().indexOf(group) == 0) {
                            textView.setPadding(0, (int)(15*metrix.density), 0, (int) (15*metrix.density));
                        } else {
                            textView.setPadding(0, (int)(15*metrix.density), 0, (int)(15*metrix.density));
                        }

                        textView.setPadding((int) (17 * getResources().getDisplayMetrics().density), (int) (15 * metrix.density), (int) (17 * getResources().getDisplayMetrics().density), (int)(15*metrix.density));

                        container.addView(textView);
                    }

                    for (int i = 0; i < group.getItems().size(); i++) {
                        Class itemClass = group.getItems().get(i).getClass();
                        if (itemClass.equals(GroupItemCalendar.class)) {
                            final GroupItemCalendar ef =
                                    (GroupItemCalendar) group.getItems().get(i);

                            LinearLayout ll = new LinearLayout(this);
                            ll.setOrientation(LinearLayout.VERTICAL);

                            TextView label = new TextView(this);
                            label.setText(ef.getLabel());
                            label.setTextSize(16);
                            label.setPadding(0, 0, 0, (int) (10 * metrix.density));

                            label.setLayoutParams(new LayoutParams(
                                    LayoutParams.WRAP_CONTENT,
                                    LayoutParams.WRAP_CONTENT));

                            label.setTextColor(Statics.color3);

                            final Button value = new Button(this);
                            value.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
                                    LayoutParams.WRAP_CONTENT));
                            value.setTextSize(22);
                            value.setGravity(Gravity.LEFT| Gravity.CENTER_VERTICAL);
                            value.setTextColor(Color.parseColor("#E6000000"));
                            value.setBackgroundResource(R.drawable.edittext_back);
                            Drawable img = getResources().getDrawable( R.drawable.arrow2x);
                            if (metrix.densityDpi == DisplayMetrics.DENSITY_XHIGH) {
                                Bitmap bitmap = ((BitmapDrawable) img).getBitmap();

                                bitmap = Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * 1.5), (int) (bitmap.getHeight() * 1.5), false);
                                img = new BitmapDrawable(bitmap);
                            }
                            value.setCompoundDrawablesWithIntrinsicBounds(null, null, img, null);
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                                    LayoutParams.WRAP_CONTENT);
                            params.setMargins(0,0,0,(int)(28* metrix.density));

                            value.setLayoutParams(params);

                            DialogDatePicker picker = new DialogDatePicker(2010, 5,5, value, ef);
                            final DatePickerDialog dialog = new DatePickerDialog(CustomFormPlugin.this, picker,  2010, 5,5);

                            Date date = ef.getDate();
                            if (ef.isSet()) {
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(date);

                                String datePattern = getResources().getString(R.string.data_picker_pattern);
                                DateFormat DATE_FORMAT = new SimpleDateFormat(datePattern);
                                String s = DATE_FORMAT.format(date);
                                value.setText(s);
                                dialog.updateDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                            }
                            else
                            {
                                String hint =getResources().getString(R.string.data_picker_hint);
                                value.setTextColor(Color.parseColor("#8c8c8c"));
                                value.setText(hint);
                            }


                            value.setOnClickListener(new OnClickListener() {

                                @Override
                                public void onClick(View view) {

                                        dialog.show();
                                }
                            });

                            ll.addView(label);
                            ll.addView(value);
                            ll.setBackgroundColor(Statics.color1);

                            groupLL.addView(ll);
                        } else if (itemClass.equals(GroupItemCheckBox.class)) {
                            final GroupItemCheckBox ef =
                                    (GroupItemCheckBox) group.getItems().get(i);

                            LinearLayout ll = new LinearLayout(this);
                            ll.setPadding(0,0,0,0);

                            TextView label = new TextView(this);
                            label.setTextColor(Color.BLACK);
                            label.setText(ef.getLabel());
                            label.setTextSize(16);
                            label.setPadding((int) (10 * metrix.density), 0, 0, (int) (10 * metrix.density));
                            label.setMaxLines(3);
                            label.setTextColor(Statics.color4);
                            label.setGravity(Gravity.LEFT);
                            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                                    LayoutParams.WRAP_CONTENT,
                                    LayoutParams.WRAP_CONTENT);
                            labelParams.setMargins(0,0,0, (int )(metrix.density));
                            label.setLayoutParams(labelParams);

                            final CheckBox value = createCheckBox(this, ef);
                            label.setOnClickListener(new OnClickListener() {
                                public void onClick(View arg0) {
                                    value.performClick();
                                }
                            });


                            ll.addView(value);
                            ll.addView(label);
                            ll.setBackgroundColor(Statics.color1);
                            groupLL.addView(ll);
                        } else if (itemClass.equals(GroupItemDropDown.class)) {
                            final GroupItemDropDown ef =
                                    (GroupItemDropDown) group.getItems().get(i);

                            LinearLayout ll = new LinearLayout(this);
                            ll.setOrientation(LinearLayout.VERTICAL);

                            TextView label = new TextView(this);
                            label.setText(ef.getLabel());
                            label.setTextSize(16);
                            label.setPadding(0, 0, 0, (int) (10 * metrix.density));
                            label.setTextColor(Statics.color3);

                            label.setLayoutParams(new LayoutParams(
                                    LayoutParams.WRAP_CONTENT,
                                    LayoutParams.WRAP_CONTENT));


                            final Button value = new Button (this);
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                            params.setMargins(0,0,0,28);
                            value.setLayoutParams(params);
                                    value.setTextSize(22);
                            value.setGravity(Gravity.LEFT| Gravity.CENTER_VERTICAL);
                            value.setTextColor(Color.parseColor("#E6000000"));
                            value.setHeight((int) (44 * metrix.density));
                            value.setEllipsize(TextUtils.TruncateAt.END);
                            value.setSingleLine();
                            value.setBackgroundResource(R.drawable.edittext_back);

                            Drawable img = getResources().getDrawable(R.drawable.arrow2x);
                            if (metrix.densityDpi == DisplayMetrics.DENSITY_XHIGH ) {
                                Bitmap bitmap = ((BitmapDrawable) img).getBitmap();

                                bitmap = Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth()*1.5), (int)(bitmap.getHeight()*1.5), false);
                                img = new BitmapDrawable(bitmap);
                            }
                              value.setCompoundDrawablesWithIntrinsicBounds(null, null, img, null);
                            final ArrayList<String> dropItems = new ArrayList<String>();

                            for (Iterator<GroupItemDropDownItem> it1 = ef.getItems().iterator(); it1.hasNext();) {
                                dropItems.add(it1.next().getValue());
                            }
                            value.setText(dropItems.get(0));
                            final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                                    android.R.layout.simple_spinner_item,
                                    dropItems);

                            value.setOnClickListener(new OnClickListener() {
                                @SuppressLint("NewApi")
                                @Override
                                public void onClick(View view) {
                                    try {
                                        CharSequence[] list = dropItems.toArray(new CharSequence[dropItems.size()]);

                                        AlertDialog.Builder builder = new AlertDialog.Builder(CustomFormPlugin.this);
                                        builder.setItems(list, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int item) {
                                                value.setText(dropItems.get(item));
                                                ef.setSelectedIndex(item);
                                            }
                                        });
                                        AlertDialog alert = builder.create();
                                        alert.show();


                                    }catch(Throwable e)
                                    {
                                        e.printStackTrace();
                                    }
                                }
                            });

                            value.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

                            ll.addView(label);
                            ll.addView(value);
                            ll.setPadding(0,0,0, 28);
                            ll.setBackgroundColor(Statics.color1);
                            groupLL.addView(ll);
                        } else if (itemClass.equals(GroupItemEntryField.class)) { 
                            final GroupItemEntryField ef =
                                    (GroupItemEntryField) group.getItems().get(i);

                            LinearLayout ll = new LinearLayout(this);
                            ll.setOrientation(LinearLayout.VERTICAL);

                            TextView label = new TextView(this);
                            label.setTextColor(Statics.color3);
                            label.setText(ef.getLabel());
                            label.setTextSize(16);
                            label.setPadding(0, 0, 0, (int) (10 * metrix.density));

                            label.setLayoutParams(new LayoutParams(
                                    LayoutParams.WRAP_CONTENT,
                                    LayoutParams.WRAP_CONTENT));

                            EditText value = new EditText(this);
                            value.setHint(ef.getValue());
                            ef.setValue("");
                            value.setHintTextColor(Color.parseColor("#8c8c8c"));

                            value.setTextColor(Color.parseColor("#E6000000"));
                                    value.setMaxLines(3);

                            value.setVerticalScrollBarEnabled(true);
                            value.setTextSize(22);
                            value.setWidth((int)(44 * metrix.density));
                            value.setHeight((int)(44 * metrix.density));
                            //value.setBackgroundColor(Statics.color5);
                            if (ef.getType().equalsIgnoreCase("number")) {
                                value.setInputType(InputType.TYPE_CLASS_NUMBER
                                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
                            }
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                                    LayoutParams.WRAP_CONTENT);
                            params.setMargins(0,0,0,(int)(28* metrix.density));

                            value.setLayoutParams(params);
                            value.addTextChangedListener(new TextWatcher() {
                                public void beforeTextChanged(CharSequence arg0,
                                                              int arg1, int arg2, int arg3) {
                                }

                                public void onTextChanged(CharSequence arg0,
                                                          int arg1, int arg2, int arg3) {
                                }

                                public void afterTextChanged(Editable arg0) {
                                    ef.setValue(arg0.toString());
                                }
                            });
                            value.setBackgroundResource(R.drawable.edittext_back);

                            ll.addView(label);
                            ll.addView(value);
                            groupLL.addView(ll);
                        } else if (itemClass.equals(GroupItemRadioButton.class)) {
                            final GroupItemRadioButton ef =
                                    (GroupItemRadioButton) group.getItems().get(i);

                            LinearLayout ll = new LinearLayout(this);

                            TextView label = new TextView(this);
                            label.setText(ef.getLabel());
                            label.setTextSize(16);
                            label.setPadding((int) (10 * metrix.density), 0, 0, (int) (10 * metrix.density));
                            label.setTextColor(Statics.color4);
                            label.setMaxLines(3);
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                    LayoutParams.WRAP_CONTENT,
                                    LayoutParams.WRAP_CONTENT);
                            params.setMargins(0,0,0, (int) (20* metrix.density));
                            label.setLayoutParams(params);

                            final RadioButton value = createRadioButton(this, ef, groupLL);

                            label.setOnClickListener(new OnClickListener() {
                                public void onClick(View arg0) {
                                    value.performClick();
                                }
                            });

                            ll.addView(value);
                            ll.addView(label);
                            ll.setBackgroundColor(Statics.color1);
                            groupLL.addView(ll);
                            value.setChecked(ef.isSelected());
                        } else if (itemClass.equals(GroupItemTextArea.class)) { 
                            final GroupItemTextArea ef =
                                    (GroupItemTextArea) group.getItems().get(i);

                            LinearLayout ll = new LinearLayout(this);
                            ll.setOrientation(LinearLayout.VERTICAL);

                            TextView label = new TextView(this);
                            label.setText(ef.getLabel());
                            label.setTextSize(17);
                            label.setPadding(0, 0, 0,  (int) (10 * metrix.density));
                            label.setTextColor(Statics.color3);
                            label.setBackgroundColor(Color.TRANSPARENT);

                            label.setLayoutParams(new LayoutParams(
                                    LayoutParams.WRAP_CONTENT,
                                    LayoutParams.WRAP_CONTENT));

                            final ScrollView parentView = (ScrollView) findViewById(R.id.scroll);
                            parentView.setOnTouchListener(new View.OnTouchListener() {

                                public boolean onTouch(View v, MotionEvent event) {
                                    v.onTouchEvent(event);
                                    return false;
                                }
                            });

                            final EditText value = new EditText(this);
                            value.setLines(3);
                            value.setHint(ef.getValue());
                            ef.setValue("");
                            value.setHintTextColor(Color.parseColor("#8c8c8c"));
                            value.setTextColor(Color.parseColor("#E6000000"));
                            value.setTextSize(22);
                            value.setOnTouchListener(new View.OnTouchListener() {
                                @Override
                                public boolean onTouch(View v, MotionEvent event) {
                                    ViewParent parent = value.getParent();
                                    parent.requestDisallowInterceptTouchEvent(true);
                                    return false;
                                }
                            });
                            value.setBackgroundResource(R.drawable.edittext_back);
                            value.setMinHeight((int) (44 * metrix.density));

                            value.setWidth(((int)(44 * metrix.density)));
                            LinearLayout.LayoutParams para = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                                    LayoutParams.WRAP_CONTENT);
                            value.setLayoutParams(para);
                            value.addTextChangedListener(new TextWatcher() {
                                public void beforeTextChanged(CharSequence arg0,
                                        int arg1, int arg2, int arg3) {
                                }

                                public void onTextChanged(CharSequence arg0,
                                        int arg1, int arg2, int arg3) {
                                }

                                public void afterTextChanged(Editable arg0) {
                                    String res = arg0.toString().replace("\n", " ");
                                    ef.setValue(res);
                                }
                            });
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                                    LayoutParams.WRAP_CONTENT);
                            params.setMargins(0,0,0,(int)(28* metrix.density));
                            value.setLayoutParams(params);
                            ll.addView(label);
                            ll.addView(value);
                            ll.setBackgroundColor(Statics.color1);
                            groupLL.addView(ll);
                        }
                    }
                    View view = new View(this);
                    view.setBackgroundColor(Color.parseColor("#33000000"));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT);
                    params.width = 10000;
                    params.setMargins(0, 0, 0, 0);
                    view.setMinimumHeight((int)( 1*getResources().getDisplayMetrics().density));
                    view.setLayoutParams(params);
                    view.setPadding(0,0,0,28);
                    view.setMinimumWidth(10000);

                    groupLL.setPadding((int)(17*getResources().getDisplayMetrics().density), 0, (int)(17*getResources().getDisplayMetrics().density), 0);

                    container.addView(groupLL);
                    container.addView(view);
                }

                LinearLayout buttonsLayout = new LinearLayout(this);
                buttonsLayout.setGravity(Gravity.CENTER);

                for (Iterator<FormButton> it2 =
                        forms.get(0).getButtons().iterator(); it2.hasNext();) {
                    final FormButton fBtn = it2.next();

                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    ssb.append(fBtn.getLabel());
                    ssb.setSpan(new StyleSpan(Typeface.NORMAL), 0, ssb.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    Button sendBtn = new Button(this);
                    sendBtn.setText(ssb);
                    float borderSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
                    sendBtn.setTextSize(22);
                    sendBtn.setTextColor(Statics.color1);
                    sendBtn.setMinimumWidth((int)(160* getResources().getDisplayMetrics().density));
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setShape(GradientDrawable.RECTANGLE);
                    drawable.setStroke((int) borderSize, Statics.color5);
                    drawable.setColor(Statics.color5);

                    sendBtn.setBackgroundDrawable(drawable);
                    LinearLayout.LayoutParams para =  new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                    para.setMargins(0, (int) (20 * getResources().getDisplayMetrics().density), 0, (int) (15 * getResources().getDisplayMetrics().density));
                    sendBtn.setLayoutParams(para);

                    sendBtn.setOnClickListener(new OnClickListener() {
                        public void onClick(View arg0) {
                            Intent emailIntent = chooseEmailClient();
                            emailIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                                    Html.fromHtml(prepareText(forms.get(0))));
                            emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                                    forms.get(0).getSubject());
                            emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL,
                                    new String[]{forms.get(0).getAddress()});
                            startActivity(emailIntent);
                        }
                    });
                    buttonsLayout.addView(sendBtn);
                    buttonsLayout.setPadding((int)(17*getResources().getDisplayMetrics().density), 0, (int)(17*getResources().getDisplayMetrics().density), 0);
                }

                container.addView(buttonsLayout);

            }

            if (progressDialog != null) {
                progressDialog.dismiss();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Drawable prefetchDrawable(Drawable img) {
        Bitmap bitmap = ((BitmapDrawable) img).getBitmap();
        Bitmap.Config config = bitmap.getConfig();
        bitmap = bitmap.copy(config==null?Bitmap.Config.ARGB_8888:config, true);

        try {
            for (int height = 0; height < bitmap.getHeight(); height++)
                for (int width = 0; width < bitmap.getWidth(); width++) {
                    int currentColor = bitmap.getPixel(width ,height);
                    if (currentColor == Color.WHITE || currentColor == Color.TRANSPARENT)
                        continue;
                    else
                        bitmap.setPixel(width, height,  Statics.color5);
                }
        }
        catch(Throwable e)
        {
            e.printStackTrace();
        }
        return new BitmapDrawable(bitmap);
    }

    private RadioButton createRadioButton(Context customFormPlugin, final GroupItemRadioButton ef, final LinearLayout groupLL) {
        final int height = 34;
        final int width = 34;

        final RadioButton value = new RadioButton(customFormPlugin);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        params.setMargins(0,0,0,(int)(15*getResources().getDisplayMetrics().density));

        value.setLayoutParams(params);

        final GradientDrawable buttonDrawable = new GradientDrawable();
        buttonDrawable.setColor(Color.TRANSPARENT);
        value.setButtonDrawable(buttonDrawable);

        Drawable uncheckedDraw = getResources().getDrawable(R.drawable.rb_off);
        Bitmap uncheckedBitmap =((BitmapDrawable)uncheckedDraw).getBitmap();
        uncheckedBitmap = applyingColorFilter ( uncheckedBitmap);
        Drawable checkedDraw = getResources().getDrawable(R.drawable.rb_on);
        Bitmap checkedBitmap =((BitmapDrawable)checkedDraw).getBitmap();
        checkedBitmap = applyingColorFilter ( checkedBitmap);

        if (value.isChecked())
            value.setBackgroundResource(R.drawable.radiobtn_on2x);
        else
        value.setBackgroundResource(R.drawable.radiobtn_off);

        value.setWidth((int) (width * getResources().getDisplayMetrics().density));
        value.setHeight((int)(height*getResources().getDisplayMetrics().density ));
        try {
            final Drawable checked = getResources().getDrawable(R.drawable.radiobtn_on2x);
        }catch (Throwable ex)
        {
            ex.printStackTrace();
        }
        value.setOnCheckedChangeListener(
                new OnCheckedChangeListener() {
                    public void onCheckedChanged(
                            CompoundButton arg0,
                            boolean arg1) {
                        ef.setSelected(arg1);
                        if (arg1) {
                            value.setBackgroundResource(R.drawable.radiobtn_on2x);
                            value.setWidth((int) (width * getResources().getDisplayMetrics().density));
                            value.setHeight((int)(height*getResources().getDisplayMetrics().density ));
                            for (int viewNumb = 0; viewNumb < groupLL.getChildCount(); viewNumb++) {
                                try {
                                    LinearLayout childLL = (LinearLayout) groupLL.getChildAt(viewNumb);
                                    RadioButton changingCheckBox = (RadioButton) childLL.getChildAt(0);
                                    if (changingCheckBox.isChecked()) {
                                        if (value != changingCheckBox) {
                                            changingCheckBox.setChecked(false);
                                            changingCheckBox.setBackgroundResource(R.drawable.radiobtn_off);

                                            value.setWidth((int) (width * getResources().getDisplayMetrics().density));
                                            value.setHeight((int)(height*getResources().getDisplayMetrics().density ));
                                        }
                                    }
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                });
        value.setChecked(false);
    return value;
    }

    private Bitmap applyingColorFilter(Bitmap uncheckedBitmap) {
        Bitmap mutableBitmap = null;
        try {
            Bitmap.Config config = uncheckedBitmap.getConfig();
            mutableBitmap = uncheckedBitmap.copy(config==null?Bitmap.Config.ARGB_8888:config, true);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
        int defaultColor = Color.rgb(60,150,2);
        int blackColor = Color.rgb(48,120,2);
        int lightColor = Statics.color5;

        int a_diff = Color.alpha(lightColor)+ (Color.alpha(blackColor) - Color.alpha(defaultColor));
        int r_diff = Color.red(lightColor)+ (Color.red(blackColor) - Color.red(defaultColor));
        int g_diff = Color.green(lightColor)+ (Color.green(blackColor) - Color.green(defaultColor));
        int b_diff = Color.blue(lightColor)+ (Color.blue(blackColor) - Color.blue(defaultColor));

        int darkColor = Color.argb(a_diff, r_diff, g_diff, b_diff);

        for (int h = 0; h < mutableBitmap.getHeight(); h++)
            for (int w = 0; w < mutableBitmap.getWidth(); w++)
            {
                int color = mutableBitmap.getPixel(h,w);
                if (h>mutableBitmap.getHeight() /3  && h < 2*mutableBitmap.getHeight()/3 && w > mutableBitmap.getWidth()/3 && w < 2*mutableBitmap.getWidth()/3 )
                    continue;
                if (color == Color.WHITE || color == Color.TRANSPARENT || color == Color.BLACK || (Color.green(color)< 119&& Color.red(color)<40))
                    continue;
                if (Color.green(color)  > 130)
                    mutableBitmap.setPixel(h,w, lightColor);
                else mutableBitmap.setPixel(h,w, darkColor);
            }
        return mutableBitmap;
    }


    private CheckBox createCheckBox(Context customFormPlugin, final GroupItemCheckBox ef) {
        final CheckBox checkBox = new CheckBox(customFormPlugin);
        final GradientDrawable buttonDrawable = new GradientDrawable();
        final int height = 34;
        final int width = 34;
        buttonDrawable.setColor(Color.TRANSPARENT);
        checkBox.setWidth((int) (width * getResources().getDisplayMetrics().density));
        checkBox.setHeight((int)(height*getResources().getDisplayMetrics().density ));
        checkBox.setButtonDrawable(buttonDrawable);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        params.setMargins(0,0,0,(int)(15*getResources().getDisplayMetrics().density));
        checkBox.setLayoutParams(params);
        checkBox.setBackgroundResource(R.drawable.checkbox_off);

        checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                {
                    checkBox.setBackgroundResource(R.drawable.checkbox_on);
                    checkBox.setWidth((int) (width * getResources().getDisplayMetrics().density));
                    checkBox.setHeight((int)(height*getResources().getDisplayMetrics().density ));
                }
                else {
                    checkBox.setBackgroundResource(R.drawable.checkbox_off);
                    checkBox.setWidth((int) (width * getResources().getDisplayMetrics().density));
                    checkBox.setHeight((int) (height * getResources().getDisplayMetrics().density));

                }
                ef.setChecked(b);
            }
        });
        checkBox.setChecked(ef.isChecked());
        return checkBox;
    }

    private Intent chooseEmailClient() {
        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        final PackageManager pm = getPackageManager();
        final List<ResolveInfo> matches = pm.queryIntentActivities(intent, 0);
        ResolveInfo best = null;

        // trying to find gmail client
        for (final ResolveInfo info : matches) {
            if (info.activityInfo.packageName.endsWith(".gm")
                    || info.activityInfo.name.toLowerCase().contains("gmail")) {
                best = info;
            }
        }

        if (best == null) {
            // if there is no gmail client trying to fing internal email client
            for (final ResolveInfo info : matches) {
                if (info.activityInfo.name.toLowerCase().contains("mail")) {
                    best = info;
                }
            }
        }
        if (best != null) {
            intent.setClassName(best.activityInfo.packageName, best.activityInfo.name);
        }

        return intent;
    }

    /**
     * Prepares email text to send.
     * @param form configured user form
     * @return email text
     */
    private String prepareText(Form form) {
        try {

            StringBuilder sb = new StringBuilder();

            for (Iterator<Group> it = form.getGroups().iterator(); it.hasNext();) {
                Group group = it.next();

                sb.append("<b>");
                sb.append(group.getTitle());
                sb.append("</b><br/>");

                for (Iterator<GroupItem> it1 = group.getItems().iterator();
                        it1.hasNext();) {

                    GroupItem item = it1.next();

                    Class itemClass = item.getClass();
                    if (itemClass.equals(GroupItemCalendar.class)) {
                        GroupItemCalendar ef =
                                (GroupItemCalendar) item;

                        sb.append(ef.getLabel());
                        sb.append(": ");
                        if (ef.isSet()) {
                            String datePattern = getResources().getString(R.string.data_picker_pattern);
                            DateFormat DATE_FORMAT = new SimpleDateFormat(datePattern);
                            String s = DATE_FORMAT.format(ef.getDate());
                            sb.append(s);
                        }
                        sb.append("<br/>");
                    } else if (itemClass.equals(GroupItemCheckBox.class)) {
                        GroupItemCheckBox ef =
                                (GroupItemCheckBox) item;

                        sb.append(ef.getLabel());
                        sb.append(": ");
                        if (ef.isChecked()) {
                            sb.append("yes");
                        } else {
                            sb.append("no");
                        }
                        sb.append("<br/>");
                    } else if (itemClass.equals(GroupItemDropDown.class)) {
                        GroupItemDropDown ef =
                                (GroupItemDropDown) item;

                        sb.append(ef.getLabel());
                        sb.append(": ");
                        try {
                            sb.append(ef.getItems()
                                    .get(ef.getSelectedIndex()).getValue());
                        } catch (Exception e) {
                        }
                        sb.append("<br/>");
                    } else if (itemClass.equals(GroupItemEntryField.class)) {
                        GroupItemEntryField ef =
                                (GroupItemEntryField) item;

                        sb.append(ef.getLabel());
                        sb.append(": ");
                        sb.append(ef.getValue());
                        sb.append("<br/>");
                    } else if (itemClass.equals(GroupItemRadioButton.class)) {
                        GroupItemRadioButton ef =
                                (GroupItemRadioButton) item;

                        sb.append(ef.getLabel());
                        sb.append(": ");
                        if (ef.isSelected()) {
                            sb.append("yes");
                        } else {
                            sb.append("no");
                        }
                        sb.append("<br/>");
                    } else if (itemClass.equals(GroupItemTextArea.class)) {
                        GroupItemTextArea ef =
                                (GroupItemTextArea) item;

                        sb.append(ef.getLabel());
                        sb.append(": ");
                        sb.append(addSpaces(ef.getValue(),
                                ef.getLabel().length() + 2));
                        sb.append("<br>");
                    }
                }
            }

            if (widget.isHaveAdvertisement()) {
                sb.append("<br>\n (sent from <a href=\"http://ibuildapp.com\">iBuildApp</a>)");
            }

            return sb.toString();

        } catch (Exception e) {
            Log.e("ROMAN_C", "lalala", e);
            return "";
        }
    }

    /**
     * Adds spaces to prepared form text.
     * @param str prepared text
     * @param colSpaces spaces count
     * @return string with added spaces
     */
    private String addSpaces(String str, int colSpaces) {
        StringBuilder res = new StringBuilder();

        String[] substrings = str.split("\n");
        for (int i = 0; i < substrings.length; i++) {
            res.append(substrings[i]);
            if (i == (substrings.length - 1)) {
                res.append("\n");
                for (int j = 0; j < colSpaces; j++) {
                    res.append(" ");
                }
            }
        }

        return res.toString();
    }

    /**
     * Checks if module color cheme is dark.
     * @param backColor module background color
     * @return true if background color is dark, false otherwise
     */
    private boolean isChemeDark(int backColor) {
        int r = (backColor >> 16) & 0xFF;
        int g = (backColor >> 8) & 0xFF;
        int b = (backColor >> 0) & 0xFF;

        double Y = (0.299 * r + 0.587 * g + 0.114 * b);
        if (Y > 127) {
            return true;
        } else {
            return false;
        }
    }


    private String readFileToString(String pathToFile) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File(pathToFile)));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
            br.close();
        } catch (Exception e) {
        }
        return sb.toString();
    }

    private void closeActivity() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        finish();
    }
}