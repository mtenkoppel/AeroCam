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
            DjiSettings.preferences(targetContext).edit().clear().putString(DjiSettings.KEY_THEME_MODE, "dark").apply()
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            runOnMainSync {
                val method=MainActivity::class.java.getDeclaredMethod("createInitialPairingView").apply{isAccessible=true}
                val view=method.invoke(activity) as View
                view.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(755,View.MeasureSpec.EXACTLY))
                view.layout(0,0,480,755)
                capture("onboarding-PAIRING",view)
                if(view is ScrollView){view.scrollTo(0,view.getChildAt(0).height);capture("onboarding-PAIRING-bottom",view)}
                activity.finish()
            }
            finish(Activity.RESULT_OK,Bundle().apply{putString("stream","Captured implemented pairing screen\n")})
        }catch(t:Throwable){finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}
    }
    private fun capture(name:String,view:View){
        view.measure(View.MeasureSpec.makeMeasureSpec(view.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(view.height,View.MeasureSpec.EXACTLY))
        view.layout(view.left,view.top,view.right,view.bottom)
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
        if(v.backgroundTintList!=null)j.put("backgroundTint",v.backgroundTintList!!.getColorForState(v.drawableState,v.backgroundTintList!!.defaultColor))
        if(v is LinearLayout)j.put("orientation",v.orientation)
        if(v is ScrollView)j.put("scrollY",v.scrollY)
        if(v is TextView){
            j.put("text",v.text.toString()).put("textSize",v.textSize).put("color",v.currentTextColor).put("style",v.typeface?.style?:0).put("gravity",v.gravity).put("includeFontPadding",v.includeFontPadding)
            val l=v.layout
            if(l!=null){val a=JSONArray();for(i in 0 until l.lineCount){a.put(JSONObject().put("text",v.text.subSequence(l.getLineStart(i),l.getLineEnd(i)).toString().trimEnd('\n')).put("x",l.getLineLeft(i)+v.compoundPaddingLeft).put("width",l.getLineWidth(i)).put("baseline",l.getLineBaseline(i)+(v.baseline-l.getLineBaseline(0))).put("top",l.getLineTop(i)+(v.baseline-l.getLineBaseline(0))).put("bottom",l.getLineBottom(i)+(v.baseline-l.getLineBaseline(0))))};j.put("lines",a)}
        }
        if(v is Switch) {j.put("checked",v.isChecked);for((key,d) in listOf("thumb" to v.thumbDrawable,"track" to v.trackDrawable)){val b=d.bounds;j.put(key,JSONArray(listOf(b.left,b.top,b.width(),b.height())))}}
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
