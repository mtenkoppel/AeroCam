package com.karoopoc

import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.*
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.karoopoc.dji.*
import com.karoopoc.widget.*
import io.hammerhead.karooext.models.ViewConfig
import org.json.*
import java.io.File

class CaptureInstrumentation : Instrumentation() {
    private lateinit var out: File
    private var assetId = 0
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            out = File(targetContext.getExternalFilesDir(null), "figma-capture").apply { mkdirs() }
            DjiSettings.preferences(targetContext).edit().putString(DjiSettings.KEY_THEME_MODE, "dark").clear().putString(DjiSettings.KEY_THEME_MODE, "dark").apply()
            val activity = startActivitySync(Intent(targetContext, OnboardingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            val pageField=OnboardingActivity::class.java.getDeclaredField("page").apply{isAccessible=true}
            val renderer=OnboardingActivity::class.java.getDeclaredMethod("render").apply{isAccessible=true}
            val names=pageField.type.enumConstants!!
            for (enumPage in names) {
                runOnMainSync { pageField.set(activity,enumPage); renderer.invoke(activity) }
                waitForIdleSync()
                runOnMainSync { capture("onboarding-"+enumPage.toString(),activity.findViewById(android.R.id.content)) }
            }
            // Capture numeric editors individually using their real text, defaults and layout.
            for (i in listOf(1,2,4)) {
                runOnMainSync {
                    OnboardingActivity::class.java.getDeclaredField("edit").apply{isAccessible=true}.setInt(activity,i)
                    OnboardingActivity::class.java.getDeclaredField("inputDraft").apply{isAccessible=true}.set(activity, when(i){1->"250";2->"40";else->"165"})
                    pageField.set(activity,names.first{it.toString()=="THRESHOLD"});renderer.invoke(activity)
                }
                waitForIdleSync()
                runOnMainSync{capture("onboarding-THRESHOLD-$i",activity.findViewById(android.R.id.content))}
            }
            val sizes=listOf(Triple("large",Pair(60,30),Pair(480,189)),Triple("medium",Pair(60,20),Pair(480,154)),Triple("small",Pair(60,15),Pair(480,113)),Triple("half",Pair(30,15),Pair(238,113)),Triple("full",Pair(60,60),Pair(480,420)))
            val scenarios=listOf("ready","offline","reconnecting","recording","feedback","prevent-standby","sleep-confirmation")
            for ((size,grid,px) in sizes) for(scenario in scenarios) {
                runOnMainSync {
                    val recording=scenario=="recording"||scenario=="feedback"
                    val camera=DjiCameraState(configured=true,connection=when(scenario){"offline"->CameraConnectionState.DISCONNECTED;"reconnecting"->CameraConnectionState.SEARCHING;else->CameraConnectionState.CONNECTED},batteryPercent=60,recording=recording,recordingDurationSeconds=885,remainingCapacityMb=14848,remainingRecordingSeconds=7200,videoResolution=16,videoFps=3,highlightCount=4,photoState=if(scenario=="feedback")PhotoCaptureState.COMPLETE else PhotoCaptureState.IDLE,highlightFeedback=if(scenario=="feedback")targetContext.getString(R.string.widget_speed_feedback)else null,keepAwakeEnabled=scenario=="prevent-standby",keepAwakeActive=scenario=="prevent-standby")
                    val state=DjiWidgetViewState(camera,controlFeedback=if(scenario=="feedback")targetContext.getString(R.string.widget_keep_awake_on)else null,sleepConfirmation=scenario=="sleep-confirmation")
                    val config=ViewConfig(grid,px,when(size){"medium"->32;"small"->26;"half"->24;else->40})
                    for(type in listOf("video","photo","control")){
                        val parent=FrameLayout(activity)
                        val remote=when(type){"video"->DjiWidgetRenderer.video(activity,config,state,false);"photo"->DjiWidgetRenderer.photo(activity,config,state,false);else->DjiWidgetRenderer.cameraControl(activity,config,state,false)}
                        val view=remote.apply(activity,parent)
                        parent.addView(view,FrameLayout.LayoutParams(-1,-1))
                        parent.measure(View.MeasureSpec.makeMeasureSpec(px.first,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(px.second,View.MeasureSpec.EXACTLY))
                        parent.layout(0,0,px.first,px.second)
                        capture("widget-$type-$size-$scenario",parent)
                    }
                }
            }
            runOnMainSync{activity.finish()}
            finish(Activity.RESULT_OK,Bundle().apply{putString("stream","Captured native onboarding and 105 widget renders to ${out.absolutePath}\n")})
        } catch(t:Throwable){finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}
    }
    private fun capture(name:String,view:View){
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap));File(out,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        File(out,"$name.json").writeText(node(view).put("capture",name).put("density",view.resources.displayMetrics.density).put("scaledDensity",view.resources.displayMetrics.scaledDensity).toString())
    }
    private fun background(d:Drawable?):JSONObject? {
        if(d==null)return null
        return when(d){
            is ColorDrawable->JSONObject().put("color",d.color)
            is GradientDrawable->JSONObject().put("color",d.color?.defaultColor?:0).put("radius",d.cornerRadius)
            is InsetDrawable->background(d.drawable)
            is LayerDrawable-> { var found:JSONObject?=null;for(i in 0 until d.numberOfLayers){val b=background(d.getDrawable(i));if(b!=null)found=b};found }
            else->if(d.current!==d)background(d.current)else null
        }
    }
    private fun node(v:View):JSONObject {
        val j=JSONObject().put("class",v.javaClass.simpleName).put("id",runCatching{v.resources.getResourceEntryName(v.id)}.getOrDefault(""))
            .put("x",v.left).put("y",v.top).put("w",v.width).put("h",v.height).put("visibility",v.visibility)
            .put("padding",JSONArray(listOf(v.paddingLeft,v.paddingTop,v.paddingRight,v.paddingBottom))).put("alpha",v.alpha).put("enabled",v.isEnabled)
            .put("background",background(v.background))
        if(v is LinearLayout)j.put("orientation",v.orientation)
        if(v is ScrollView)j.put("scrollY",v.scrollY)
        if(v is TextView){
            j.put("text",v.text.toString()).put("textSize",v.textSize).put("color",v.currentTextColor).put("style",v.typeface?.style?:0).put("gravity",v.gravity).put("includeFontPadding",v.includeFontPadding)
            val l=v.layout
            if(l!=null){val a=JSONArray();for(i in 0 until l.lineCount){a.put(JSONObject().put("text",v.text.subSequence(l.getLineStart(i),l.getLineEnd(i)).toString().trimEnd('\n')).put("x",l.getLineLeft(i)+v.compoundPaddingLeft).put("baseline",l.getLineBaseline(i)+v.extendedPaddingTop).put("top",l.getLineTop(i)+v.extendedPaddingTop).put("bottom",l.getLineBottom(i)+v.extendedPaddingTop))};j.put("lines",a)}
        }
        if(v is Switch) j.put("checked",v.isChecked)
        if(v is ImageView && v.drawable!=null){
            val d=v.drawable;val id=R.drawable::class.java.fields.firstOrNull{f->runCatching{v.context.getDrawable(f.getInt(null))?.constantState==d.constantState}.getOrDefault(false)}
            if(id!=null)j.put("drawable",id.name)
            j.put("tint",v.imageTintList?.defaultColor).put("scaleType",v.scaleType.name)
            val matrix=FloatArray(9);v.imageMatrix.getValues(matrix);j.put("imageMatrix",JSONArray(matrix.toList())).put("intrinsicW",d.intrinsicWidth).put("intrinsicH",d.intrinsicHeight)
        }
        if(v is ViewGroup){val children=JSONArray();for(i in 0 until v.childCount){val c=v.getChildAt(i);if(c.visibility==View.VISIBLE)children.put(node(c))};j.put("children",children)}
        return j
    }
}
