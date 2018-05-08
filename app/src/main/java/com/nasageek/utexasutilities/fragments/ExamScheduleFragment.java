package com.nasageek.utexasutilities.fragments;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CalendarContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.commonsware.cwac.security.RuntimePermissionUtils;
import com.nasageek.utexasutilities.AuthCookie;
import com.nasageek.utexasutilities.MyBus;
import com.nasageek.utexasutilities.NotAuthenticatedException;
import com.nasageek.utexasutilities.R;
import com.nasageek.utexasutilities.UTLoginTask;
import com.nasageek.utexasutilities.UTilitiesApplication;
import com.nasageek.utexasutilities.activities.CampusMapActivity;
import com.nasageek.utexasutilities.model.LoadFailedEvent;
import com.squareup.okhttp.Request;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nasageek.utexasutilities.UTilitiesApplication.UTD_AUTH_COOKIE_KEY;
import static com.nasageek.utexasutilities.fragments.DoubleDatePickerDialogFragment.EVENT_PROJECTION;

public class ExamScheduleFragment extends ScheduleFragment implements ActionModeFragment,
        ActionMode.Callback, AdapterView.OnItemClickListener {

    private static final int REQUEST_CALENDAR_PERMISSION = 1;

    private ArrayList<String> exams = new ArrayList<>();
    private ListView examListview;
    private LinearLayout progressLayout;
    private TextView errorTextView;
    private View errorLayout;
    private ActionMode mode;
    private AuthCookie utdAuthCookie;
    private String TASK_TAG;
    private String selectedExam;
    private UTilitiesApplication mApp = UTilitiesApplication.getInstance();
    private RuntimePermissionUtils runtimePermissions;

    public static final String[] EVENT_PROJECTION = new String[]{
            CalendarContract.Calendars._ID, // 0
            CalendarContract.Calendars.ACCOUNT_NAME, // 1
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, // 2
            CalendarContract.Calendars.OWNER_ACCOUNT, // 3
            CalendarContract.Calendars.VISIBLE, // 4
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL // 5
    };

    // The indices for the projection array above.
    private static final int PROJECTION_ID_INDEX = 0;
    private static final int PROJECTION_ACCOUNT_NAME_INDEX = 1;
    private static final int PROJECTION_DISPLAY_NAME_INDEX = 2;

    public static ExamScheduleFragment newInstance() {
        return new ExamScheduleFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View vg = inflater.inflate(R.layout.exam_schedule_fragment_layout, container, false);
        progressLayout = (LinearLayout) vg.findViewById(R.id.examschedule_progressbar_ll);
        examListview = (ListView) vg.findViewById(R.id.examschedule_listview);
        errorLayout = vg.findViewById(R.id.examschedule_error);
        errorTextView = (TextView) vg.findViewById(R.id.tv_failure);

        if (savedInstanceState != null) {
            switch (loadStatus) {
                case NOT_STARTED:
                    // defaults should suffice
                    break;
                case LOADING:
                    progressLayout.setVisibility(View.VISIBLE);
                    errorLayout.setVisibility(View.GONE);
                    examListview.setVisibility(View.GONE);
                    break;
                case SUCCEEDED:
                    progressLayout.setVisibility(View.GONE);
                    errorLayout.setVisibility(View.GONE);
                    examListview.setVisibility(View.VISIBLE);
                    break;
                case FAILED:
                    progressLayout.setVisibility(View.GONE);
                    errorLayout.setVisibility(View.VISIBLE);
                    examListview.setVisibility(View.GONE);
                    break;
            }
        }

        if (!utdAuthCookie.hasCookieBeenSet()) {
            progressLayout.setVisibility(View.GONE);
            errorTextView.setText(getString(R.string.login_first));
            errorTextView.setVisibility(View.VISIBLE);
        } else if (loadStatus == LoadStatus.NOT_STARTED && mApp.getCachedTask(TASK_TAG) == null) {
            loadStatus = LoadStatus.LOADING;
            prepareToLoad();
            FetchExamDataTask task = new FetchExamDataTask(TASK_TAG);
            task.execute(false);
        } else {
            setupAdapter();
        }
        return vg;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            exams = savedInstanceState.getStringArrayList("exams");
        }
        setHasOptionsMenu(true);
        runtimePermissions = new RuntimePermissionUtils(getActivity());
        utdAuthCookie = mApp.getAuthCookie(UTD_AUTH_COOKIE_KEY);
        TASK_TAG = getClass().getSimpleName();
    }

    @Override
    public void onStart() {
        super.onStart();
        MyBus.getInstance().register(this);
    }

    @Override
    public void onStop() {
        MyBus.getInstance().unregister(this);
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList("exams", exams);
    }

    private void setupAdapter() {
        ExamAdapter adapter = new ExamAdapter(getActivity(), exams);
        examListview.setAdapter(adapter);
    }

    /**
     * Prepares and exports exam schedule to google calendar using the
     * PickCalendarDialogFragment
     */
    private void exportExams() {
        if (exams != null) {
            ArrayList<ContentValues> examList = new ArrayList<>();

            for (String exam : exams) {
                String[] splitExam = exam.replace(",", "").split("\\^");
                String title = splitExam[1] + " " + splitExam[2] + " FINAL EXAM";

                if (splitExam.length > 3) {
                    try {
                        //check to see if there is a makeup exam
                        if (splitExam[3].contains("\n")) {
                                ArrayList<Date> parsedDates = parseDates(splitExam[3]);
                                String building1 = "", building2 = "";
                                try {
                                    building1 = splitExam[4].split("\n")[0].substring(2);
                                    building2 = splitExam[4].split("\n")[1].substring(2);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                ContentValues contentValues1 = new ContentValues();
                                ContentValues contentValues2 = new ContentValues();
                                contentValues1.put(CalendarContract.Events.TITLE, title + " " + splitExam[4].split("\n")[0].substring(0, 2));
                                contentValues2.put(CalendarContract.Events.TITLE, title + " " + splitExam[4].split("\n")[1].substring(0, 2));
                                contentValues1.put(CalendarContract.Events.EVENT_LOCATION, building1);
                                contentValues2.put(CalendarContract.Events.EVENT_LOCATION, building2);
                                //Colors don't actually change for some reason, maybe change to new calendar instance?
                                contentValues1.put(CalendarContract.Events.EVENT_COLOR, Color.RED);
                                contentValues2.put(CalendarContract.Events.EVENT_COLOR, Color.MAGENTA);
                                contentValues1.put(CalendarContract.Events.DTSTART, parsedDates.get(0).getTime());
                                contentValues2.put(CalendarContract.Events.DTSTART, parsedDates.get(2).getTime());
                                contentValues1.put(CalendarContract.Events.DURATION, startEndToDuration(parsedDates.get(0), parsedDates.get(1)));
                                contentValues2.put(CalendarContract.Events.DURATION, startEndToDuration(parsedDates.get(2), parsedDates.get(3)));
                                contentValues1.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone
                                        .getTimeZone("US/Central").getID());
                                contentValues2.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone
                                        .getTimeZone("US/Central").getID());

                                examList.add(contentValues1);
                                examList.add(contentValues2);

                        } else {
                                ArrayList<Date> parsedDates = parseDates(splitExam[3]);

                                String building = "";
                                try {
                                    building = splitExam[4].split("\n")[0];
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                ContentValues contentValues1 = new ContentValues();
                                contentValues1.put(CalendarContract.Events.TITLE, title);
                                contentValues1.put(CalendarContract.Events.EVENT_LOCATION, building);
                                contentValues1.put(CalendarContract.Events.EVENT_COLOR, Color.RED);
                                contentValues1.put(CalendarContract.Events.DTSTART, parsedDates.get(0).getTime());
                                contentValues1.put(CalendarContract.Events.DURATION, startEndToDuration(parsedDates.get(0), parsedDates.get(1)));
                                contentValues1.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone
                                        .getTimeZone("US/Central").getID());

                                examList.add(contentValues1);

                        }

                    } catch (Exception e) {
                        Toast.makeText(getActivity(),
                                "Error parsing " + title
                                        + " time. Export canceled.",
                                Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }
                }
            }

            if (examList.size() == 0) {
                Toast.makeText(getActivity(),
                        "You have no exams this semester!", Toast.LENGTH_SHORT).show();
                return;
            }

            ContentResolver cr = getActivity().getContentResolver();
            Uri uri = CalendarContract.Calendars.CONTENT_URI;

            //Check to make sure we have calendar permission before trying to access it
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_CALENDAR},
                        REQUEST_CALENDAR_PERMISSION);
            }

            // show them Google Calendars where they are either:
            // owner, editor, contributor, or domain admin (700, 600, 500, 800 respectively)
            Cursor cur = cr.query(uri, EVENT_PROJECTION, "((" + CalendarContract.Calendars.ACCOUNT_TYPE
                            + " = ?) AND ((" + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " = ?) OR "
                            + "(" + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " = ?) OR " + "("
                            + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " = ?) OR " + "("
                            + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " = ?)))",
                    new String[]{"com.google", "800", "700", "600", "500"},
                    null);
            ArrayList<String> calendars = new ArrayList<>();
            ArrayList<Integer> indices = new ArrayList<>();

            // If no calendars are available, let them know
            if (cur == null || cur.getCount() == 0) {
                Toast.makeText(getActivity(),
                        "There are no available calendars to export to.", Toast.LENGTH_LONG).show();
                return;
            }
            while (cur.moveToNext()) {
                long calID = cur.getLong(PROJECTION_ID_INDEX);
                String displayName = cur.getString(PROJECTION_DISPLAY_NAME_INDEX);
                String accountName = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX);
                calendars.add(displayName + " ^^ " + accountName);

                // going to hope that they don't have so many calendars that I actually need a long
                indices.add((int) calID);
            }
            cur.close();

            FragmentManager fm = getActivity().getSupportFragmentManager();
            PickCalendarDialogFragment pcdf = PickCalendarDialogFragment.newInstance(
                    indices, calendars, examList);
            pcdf.show(fm, "fragment_pick_calendar");
        }
    }

    /*
     * Converts a start and end time to a duration in the RFC2445 format
     */
    private String startEndToDuration(Date startTime, Date endTime) {
        int minutesDur = (int) ((endTime.getTime() - startTime.getTime()) / (1000 * 60));
        return "P" + minutesDur + "M";
    }

    /**
     * This method generates date objects from exam strings
     * @param exam contains one exam's parameters
     * @return arraylist of dates in pairs of 2
     * @throws ParseException if date format is not parseable
     */
    private ArrayList<Date> parseDates(String exam) throws ParseException {
        ArrayList<Date> dates = new ArrayList<>();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy EEE MMM dd hh aa", Locale.US);
        String[] diffs = exam.split("\n");
        int year = Calendar.getInstance().get(Calendar.YEAR);

        if(diffs.length > 1) {
            for(String splitExam : diffs) {
                String[] time = splitExam.split(" ");
                String[] hours = time[4].split("-");
                String base = year + " " +time[1].substring(0, 3) + " " + time[2].substring(0, 3) + " " + time[3];
                String type = time[5], type1 = time[5];
                if(!type.equalsIgnoreCase("am") && !type.equalsIgnoreCase("pm")) {
                    if(type.equalsIgnoreCase("n")) {
                        type = "AM";
                        type1 = "PM";
                    }
                }

                dates.add(simpleDateFormat.parse(base + " " + hours[0] + " " + type));
                dates.add(simpleDateFormat.parse(base + " " + hours[1] + " " + type1));
            }
        } else {
            String[] time = diffs[0].split(" ");
            String[] hours = time[3].split("-");
            String base = year + " " +time[0].substring(0, 3) + " " + time[1].substring(0, 3) + " " + time[2];
            String type = time[4], type1 = time[4];
            if(!type.equalsIgnoreCase("am") && !type.equalsIgnoreCase("pm")) {
                if(type.equalsIgnoreCase("n")) {
                    type = "AM";
                    type1 = "PM";
                }
            }
            dates.add(simpleDateFormat.parse(base + " " + hours[0] + " " + type));
            dates.add(simpleDateFormat.parse(base + " " + hours[1] + " " + type1));
        }
        return dates;
    }

    @Override
    public ActionMode getActionMode() {
        return mode;
    }

    private void prepareToLoad() {
        progressLayout.setVisibility(View.VISIBLE);
        examListview.setVisibility(View.GONE);
        errorLayout.setVisibility(View.GONE);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.setTitle("Exam Info");
        MenuInflater inflater = getActivity().getMenuInflater();
        String[] elements = selectedExam.split("\\^");
        if (elements.length >= 3) { // TODO: check this?
            if (elements[2].contains("The department")
                    || elements[2]
                    .contains("Information on final exams is available for Nine-Week Summer Session(s) only.")
                    || elements.length <= 4) {
                return true;
            }
        } else {
            return true;
        }
        inflater.inflate(R.menu.schedule_action_mode, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean examsScheduled = (exams != null && exams.size() > 0);
        menu.findItem(R.id.export_exams).setEnabled(examsScheduled);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.exam_schedule_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.export_exams: {
                if (runtimePermissions.hasPermission(Manifest.permission.WRITE_CALENDAR)) {
                    exportExams();
                } else {
                    requestPermissions(new String[]{Manifest.permission.WRITE_CALENDAR},
                            REQUEST_CALENDAR_PERMISSION);
                }
            } break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CALENDAR_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    exportExams();
                }
            } break;
        }
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.locate_class:
                ArrayList<String> building = new ArrayList<>();
                Intent map = new Intent(getActivity().getString(R.string.building_intent), null,
                        getActivity(), CampusMapActivity.class);

                String[] elements = selectedExam.split("\\^");
                if (elements.length >= 5) {
                    building.add(elements[4].split(" ")[0]);
                    map.putStringArrayListExtra("buildings", building);
                    // map.setData(Uri.parse(elements[4].split(" ")[0]));
                    getActivity().startActivity(map);
                    return true;
                } else {
                    Toast.makeText(getActivity(), "Your exam's location could not be found",
                            Toast.LENGTH_SHORT).show();
                }
        }
        return true;

    }

    @Override
    public void onDestroyActionMode(ActionMode mode) { }

    static class FetchExamDataTask extends UTLoginTask<Boolean, Void, List<String>> {

        public FetchExamDataTask(String tag) {
            super(tag, "https://utdirect.utexas.edu/registrar/exam_schedule.WBX");
        }

        @Override
        protected List<String> doInBackground(Boolean... params) {
            Boolean recursing = params[0];
            List<String> examlist = new ArrayList<>();

            Request request = new Request.Builder()
                    .url(reqUrl)
                    .build();
            String pagedata;

            try {
                pagedata = fetchData(request);
            } catch (IOException e) {
                errorMsg = "UTilities could not fetch your exam schedule";
                e.printStackTrace();
                cancel(true);
                return null;
            } catch (NotAuthenticatedException e) {
                e.printStackTrace();
                cancel(true);
                return null;
            }

            if (pagedata.contains("will be available approximately three weeks")) {
                cancel(true);
                errorMsg = "'Tis not the season for final exams.\nTry back later!" +
                            "\n(about 3 weeks before they begin)";
                return null;
            } else if (pagedata.contains("Our records indicate that you are not enrolled" +
                    " for the current semester.")) {
                cancel(true);
                errorMsg = "You aren't enrolled for the current semester.";
                return null;
            }

            Pattern rowpattern = Pattern.compile("<tr >.*?</tr>", Pattern.DOTALL);
            Matcher rowmatcher = rowpattern.matcher(pagedata);

            while (rowmatcher.find()) {
                String rowstring = "";
                String row = rowmatcher.group();
                if (row.contains("Unique") || row.contains("Home Page")) {
                    continue;
                }

                Pattern fieldpattern = Pattern.compile("<td.*?>(.*?)</td>", Pattern.DOTALL);
                Matcher fieldmatcher = fieldpattern.matcher(row);
                while (fieldmatcher.find()) {
                    String field = fieldmatcher.group(1).replace("&nbsp;", " ").trim()
                            .replace("\t", "");
                    Spanned span = Html.fromHtml(field);
                    String out = span.toString();
                    rowstring += out + "^";
                }
                examlist.add(rowstring);
            }
            return examlist;
        }

        @Override
        protected void onPostExecute(List<String> result) {
            super.onPostExecute(result);
            MyBus.getInstance().post(new LoadSucceededEvent(getTag(), result));
        }
    }

    @Subscribe
    public void loadFailed(LoadFailedEvent event) {
        if (event.tag.equals(TASK_TAG)) {
            loadStatus = LoadStatus.FAILED;
            progressLayout.setVisibility(View.GONE);
            examListview.setVisibility(View.GONE);
            errorTextView.setText(event.errorMessage);
            errorLayout.setVisibility(View.VISIBLE);
        }
    }

    @Subscribe
    public void loadSucceeded(LoadSucceededEvent event) {
        if (event.tag.equals(TASK_TAG)) {
            loadStatus = LoadStatus.SUCCEEDED;
            exams.clear();
            exams.addAll(event.exams);
            progressLayout.setVisibility(View.GONE);
            setupAdapter();
            examListview.setOnItemClickListener(this);
            examListview.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        selectedExam = exams.get(position);
        mode = ((AppCompatActivity)getActivity()).startSupportActionMode(this);
    }

    static class ExamAdapter extends ArrayAdapter<String> {

        public ExamAdapter(Context c, ArrayList<String> objects) {
            super(c, 0, objects);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int i) {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            String[] examdata = getItem(position).split("\\^");
            boolean examRequested = false, summerSession = false;
            String id = "", name = "", date = "", location = "", unique = "";

            // TODO: I hate doing these try/catches, find a better solution so I
            // know when stuff goes wrong? ACRA?
            try {
                examRequested = !examdata[2].contains("The department");
                summerSession = examdata[2]
                        .contains("Information on final exams is available for Nine-Week Summer Session(s) only.");

                unique = examdata[0];
                id = examdata[1];
                name = examdata[2];
                date = "";
                location = "";
                if (examRequested && !summerSession) {
                    if (examdata.length >= 4) {
                        date = examdata[3];
                    }
                    if (examdata.length >= 5) {
                        location = examdata[4];
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                ex.printStackTrace();
            }
            String course;

            View vg = convertView;
            if (vg == null) {
                vg = LayoutInflater.from(getContext())
                        .inflate(R.layout.exam_item_view, parent, false);
            }
            TextView courseview = (TextView) vg.findViewById(R.id.exam_item_header_text);
            TextView left = (TextView) vg.findViewById(R.id.examdateview);
            TextView right = (TextView) vg.findViewById(R.id.examlocview);

            if (!examRequested || summerSession) {
                course = id + " - " + unique;
                left.setText(name);
                right.setVisibility(View.INVISIBLE);
            } else {
                course = id + " " + name;
                left.setText(date);
                right.setVisibility(View.VISIBLE);
                right.setText(location);
            }
            courseview.setText(course);
            return vg;
        }
    }

    static class LoadSucceededEvent {
        public List<String> exams;
        public String tag;

        public LoadSucceededEvent(String tag, List<String> exams) {
            this.tag = tag;
            this.exams = exams;
        }
    }
}
