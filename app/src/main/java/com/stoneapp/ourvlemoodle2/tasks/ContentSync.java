/*
 * Copyright 2016 Matthew Stone and Romario Maxwell.
 *
 * This file is part of OurVLE.
 *
 * OurVLE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OurVLE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OurVLE.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.stoneapp.ourvlemoodle2.tasks;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.text.Html;

import java.util.ArrayList;
import java.util.List;

import com.activeandroid.ActiveAndroid;
import com.activeandroid.query.Select;
import com.stoneapp.ourvlemoodle2.activities.CourseViewActivity;
import com.stoneapp.ourvlemoodle2.models.MoodleModule;
import com.stoneapp.ourvlemoodle2.models.MoodleModuleContent;
import com.stoneapp.ourvlemoodle2.models.MoodleSection;
import com.stoneapp.ourvlemoodle2.models.MoodleCourse;
import com.stoneapp.ourvlemoodle2.rest.MoodleRestCourseContent;
import com.stoneapp.ourvlemoodle2.util.MoodleConstants;
import com.stoneapp.ourvlemoodle2.R;
import com.stoneapp.ourvlemoodle2.util.SettingsUtils;

public class ContentSync {

    private int courseid;
    private long coursepid;
    private long siteid;
    private Context context;
    private boolean first_update; // flag to check whether its a first sync
    private String token; // url token
    List<MoodleSection> sections;

    public ContentSync(int courseid, long coursepid, long siteid, String token, Context context) {
        this.courseid = courseid;
        this.coursepid = coursepid;
        this.siteid = siteid;
        this.context = context;
        this.token = token;

    }


    public boolean syncContent() {

        MoodleRestCourseContent mcontent = new MoodleRestCourseContent(token);
        sections = mcontent.getSections(courseid + ""); // gets the sections from api call

        if (sections == null) // check if there are no sections
            return false;

        if (sections.size() == 0)
            return false;


        ActiveAndroid.beginTransaction();
        try {
            //deleteStaleSections();
            for (int i = 0; i < sections.size(); i++) {
                final MoodleSection section = sections.get(i);
                section.setCourseid(courseid);
                section.setParentid(coursepid);

                MoodleSection db_section = MoodleSection.findOrCreateFromJson(section); // saves contact to database
                syncModules(db_section.getModules(),db_section.getSectionid(),db_section.getId());
            }
            ActiveAndroid.setTransactionSuccessful();
        }finally {
            ActiveAndroid.endTransaction();
        }
        return true;
    }





    private void syncModules(ArrayList<MoodleModule>modules, int sectionid, long sectionpid) {
        if (modules == null) // checks if there are no modules
            return;

      //  deleteStaleModules(modules);
        for(int i = 0; i < modules.size(); i++) {
            final MoodleModule module = modules.get(i);
            module.setCourseid(courseid); // set course id of module
            module.setParentid(sectionpid); // set section database id
            module.setSiteid(siteid);
            module.setSectionid(sectionid); // set section id
            MoodleModule db_module = MoodleModule.findOrCreateFromJson(module);

            syncModuleContents(db_module.getContents(), db_module.getModuleid(), db_module.getId(), sectionid); //sync module contents
        }
    }

    private void syncModuleContents(ArrayList<MoodleModuleContent> contents, int moduleid, long modulepid, int sectionid) {
        if (contents == null) // checks if there are no contents
            return;

      //  deleteStaleContent(contents);

        for (int i = 0; i < contents.size(); i++) {
            final MoodleModuleContent content = contents.get(i);
            content.setParentid(modulepid);
            content.setModuleid(moduleid);
            content.setSectionid(sectionid);
            content.setCourseid(courseid); // set course id
            content.setSiteid(siteid);
            MoodleModuleContent.findOrCreateFromJson(content);
        }
    }

    public ArrayList<MoodleSection> getContents() {
        List<MoodleSection>sections = getSectionsFromDatabase();
        List<MoodleModule>modules;
        List<MoodleModuleContent>contents ;

        for (int i = 0; i < sections.size(); i++) {

            modules = getModulesFromDatabase(sections.get(i));

            for (int j = 0; j < modules.size(); j++) {
                contents = getContentsFromDatabase(modules.get(j));
                modules.get(j).setContents(contents); // sets the content for the module
            }

            sections.get(i).setModules(modules); // set the module for the section
        }

        return new ArrayList<>(sections); // returns a list of sections
    }

    private List<MoodleSection> getSectionsFromDatabase()
    {
        return new Select().from(MoodleSection.class).where("courseid = ?",courseid).execute();
    }

    private List<MoodleModule> getModulesFromDatabase(MoodleSection section)
    {
        return new Select().from(MoodleModule.class).where("parentid = ?",section.getId()).execute();
    }

    private List<MoodleModuleContent> getContentsFromDatabase(MoodleModule module)
    {
        return new Select().from(MoodleModuleContent.class).where("parentid = ?",module.getId()).execute();
    }



    private void deleteStaleSections()
    {

        List<MoodleSection> stale_sections = new Select().from(MoodleSection.class).where("courseid = ?",courseid).execute();
        for(int i=0;i<stale_sections.size();i++)
        {
            if(!doesSectionExistInJson(stale_sections.get(i)))
            {
                MoodleSection.delete(MoodleSection.class,stale_sections.get(i).getId());
            }
        }
    }

    private boolean doesSectionExistInJson(MoodleSection section)
    {
        return sections.contains(section);
    }

    private void deleteStaleModules(List<MoodleModule> modules)
    {

        List<MoodleModule> stale_modules = new Select().from(MoodleModule.class).where("courseid = ?",courseid).execute();
        for(int i=0;i<stale_modules.size();i++)
        {
            if(!doesModuleExistInJson(modules,stale_modules.get(i)))
            {
                MoodleModule.delete(MoodleModule.class,stale_modules.get(i).getId());
            }
        }
    }

    private boolean doesModuleExistInJson(List<MoodleModule> modules,MoodleModule module)
    {
        return modules.contains(module);
    }

    private void deleteStaleContent(List<MoodleModuleContent> contents)
    {

        List<MoodleModuleContent> stale_contents = new Select().from(MoodleModuleContent.class).where("courseid = ?",courseid).execute();
        for(int i=0;i<stale_contents.size();i++)
        {
            if(!doesContentExistInJson(contents,stale_contents.get(i)))
            {
                MoodleModuleContent.delete(MoodleModuleContent.class,stale_contents.get(i).getId());
            }
        }
    }

    private boolean doesContentExistInJson(List<MoodleModuleContent> contents,MoodleModuleContent content)
    {
        return contents.contains(content);
    }



   /* public void addNotification (MoodleModule module) {
        MoodleCourse course =  new Select().from(MoodleCourse.class).where("courseid = ?", courseid).executeSingle();

        Intent resultIntent = new Intent(context, CourseViewActivity.class);
        resultIntent.putExtra("coursename", course.getShortname());
        resultIntent.putExtra("coursefname", course.getFullname());
        resultIntent.putExtra("courseid", course.getCourseid());
        resultIntent.putExtra("coursepid", course.getId());

        TaskStackBuilder stackBuilder =  TaskStackBuilder.create(context);
        stackBuilder.addParentStack(CourseViewActivity.class);

        stackBuilder.addNextIntent(resultIntent);

        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_school_24dp)
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .setContentTitle("New Content")
                .setContentText(Html.fromHtml(module.getName()).toString().trim())
                .setContentIntent(resultPendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_SOUND)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(Html.fromHtml(module.getName()).toString().trim()))
                .addAction(R.drawable.ic_clear_24dp, "Dismiss", null)
                .addAction(R.drawable.ic_add_24dp, "Download", null);

        NotificationManagerCompat mNotificationManager =
                NotificationManagerCompat.from(context);
        mNotificationManager.notify(module.getModuleid(), mBuilder.build());
    }*/
}
