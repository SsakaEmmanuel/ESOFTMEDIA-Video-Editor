package com.esoftmedia.videoeditor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val Deep=Color(0xFF090A0F); private val Panel=Color(0xFF151720); private val Cyan=Color(0xFF00E5D4); private val Purple=Color(0xFF7B2CFF)
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{EditorApp()}}}
@Composable fun EditorApp(){
 val ctx=LocalContext.current; var video by remember{mutableStateOf<Uri?>(null)}; var section by remember{mutableStateOf("Edit")}; var tool by remember{mutableStateOf("None")}; var fav by remember{mutableStateOf(false)}
 var filter by remember{mutableStateOf("Original")}; var ratio by remember{mutableStateOf("Original")}; var res by remember{mutableStateOf("1080p")}; var fmt by remember{mutableStateOf("MP4")}; var trim by remember{mutableStateOf(0f..100f)}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){video=it}; val audio=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){}
 val import={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))}
 val player=remember(video){video?.let{ExoPlayer.Builder(ctx).build().apply{setMediaItem(MediaItem.fromUri(it));prepare();playWhenReady=false}}}; DisposableEffect(player){onDispose{player?.release()}}
 MaterialTheme(colorScheme=darkColorScheme(background=Deep,surface=Panel,primary=Cyan,secondary=Purple)){Surface(Modifier.fillMaxSize(),color=Deep){Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){
 Spacer(Modifier.height(14.dp));Header();Spacer(Modifier.height(14.dp));Text("Your creative studio",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold);Text("Create. Edit. Anywhere.",color=Color.LightGray,fontSize=14.sp);Spacer(Modifier.height(14.dp))
 Preview(video,player,import);Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){Quick("Trim",Icons.Default.ContentCut,Modifier.weight(1f)){section="Edit";tool="Trim"};Quick("Music",Icons.Default.MusicNote,Modifier.weight(1f)){section="Audio";tool="Music"};Quick("Text",Icons.Default.TextFields,Modifier.weight(1f)){section="Text";tool="Text"};Quick("Effects",Icons.Default.Tune,Modifier.weight(1f)){section="Effects";tool="Effects"}}
 Spacer(Modifier.height(9.dp));Nav(section){section=it;tool="None"};Spacer(Modifier.height(8.dp))
 if(section!="Edit"||tool!="None") PanelTools(section,tool,filter,{filter=it},ratio,{ratio=it},res,{res=it},fmt,{fmt=it},trim,{trim=it},{audio.launch("audio/*")}) else Timeline(video!=null)
 Row(verticalAlignment=Alignment.CenterVertically){Text("Project",color=Color.White);Spacer(Modifier.weight(1f));IconButton(onClick={fav=!fav}){Icon(Icons.Default.Favorite,"Favourite",tint=if(fav) Color(0xFFFF4C88) else Color.Gray)};Text(if(fav)"FAVOURITE" else "OFFLINE",color=Cyan,fontSize=11.sp)}
 Spacer(Modifier.weight(1f));Bottom(section){section=it;tool="None"};Spacer(Modifier.height(8.dp))
 }}}}
@Composable private fun Header(){Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){Box(Modifier.size(46.dp).background(Panel,CircleShape).border(1.dp,Color.DarkGray,CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(30.dp).background(Brush.linearGradient(listOf(Cyan,Purple)),RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center){Text("E",color=Deep,fontSize=22.sp,fontWeight=FontWeight.Black)}};Spacer(Modifier.width(10.dp));Column{Text("ESOFTMEDIA",color=Color.White,fontWeight=FontWeight.ExtraBold,letterSpacing=1.4.sp);Text("VIDEO EDITOR",color=Purple,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)};Spacer(Modifier.weight(1f));IconButton(onClick={}){Icon(Icons.Default.Save,"Save",tint=Color.White)}}}
@Composable private fun Preview(uri:Uri?,p:ExoPlayer?,import:()->Unit){Box(Modifier.fillMaxWidth().height(205.dp).background(Brush.linearGradient(listOf(Color(0xFF181B28),Color(0xFF101116))),RoundedCornerShape(22.dp)).border(1.dp,Color(0xFF292B37),RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center){if(uri!=null&&p!=null){AndroidView(factory={PlayerView(it).apply{player=p;useController=true}},update{it.player=p},modifier=Modifier.fillMaxSize());IconButton(onClick=import,modifier=Modifier.align(Alignment.TopEnd)){Icon(Icons.Default.Add,"Replace",tint=Color.White)}}else Column(Modifier.fillMaxSize().clickable(onClick=import),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(Icons.Default.PlayArrow,null,tint=Cyan,modifier=Modifier.size(48.dp));Text("Import a video to preview",color=Color.White)};Text("PREVIEW",color=Color.LightGray,fontSize=10.sp,modifier=Modifier.align(Alignment.TopStart).padding(13.dp))}}
@Composable private fun Quick(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,m:Modifier,click:()->Unit){Column(m.background(Panel,RoundedCornerShape(14.dp)).clickable(onClick=click).padding(vertical=11.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,label,tint=Cyan);Text(label,color=Color.White,fontSize=11.sp)}}
@Composable private fun Nav(active:String,choose:(String)->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Edit","Filters","Adjust","Crop","Effects","Audio","Text","Elements","Export").forEach{Surface(color=if(it==active)Purple else Panel,shape=RoundedCornerShape(9.dp),modifier=Modifier.clickable{choose(it)}){Text(it,color=Color.White,fontSize=11.sp,modifier=Modifier.padding(horizontal=11.dp,vertical=8.dp))}}}
@Composable private fun PanelTools(s:String,t:String,f:String,sf:(String)->Unit,r:String,sr:(String)->Unit,rs:String,srs:(String)->Unit,ft:String,sft:(String)->Unit,tr:ClosedFloatingPointRange<Float>,st:(ClosedFloatingPointRange<Float>)->Unit,a:()->Unit){Column(Modifier.fillMaxWidth().heightIn(max=235.dp).background(Panel,RoundedCornerShape(15.dp)).padding(10.dp)){Text(if(t!="None")t.uppercase() else s.uppercase(),color=Cyan,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));when(s){
"Filters"->{Text("Filter presets",color=Color.White,fontSize=12.sp);Choices(listOf("Original","Cinema","Vintage","B&W","Warm","Cool","Fade","Vivid"),f,sf)}
"Adjust"->listOf("Brightness","Contrast","Saturation","Temperature","Highlights","Shadows","Sharpen").forEach{Adjust(it)}
"Crop"->{Text("Aspect ratio",color=Color.White);Choices(listOf("Original","1:1","4:5","16:9","9:16","3:2"),r,sr);Choices(listOf("Rotate 90°","Flip H","Flip V","Reset"),"",{})}
"Edit"->{Text("Trim",color=Color.White);RangeSlider(tr,st,valueRange=0f..100f);Text("Drag to select clip range",color=Color.LightGray,fontSize=11.sp);Choices(listOf("Split","Delete","Duplicate","Speed","Reverse","Freeze","Transition"),t,{})}
"Effects"->Choices(listOf("Glow","Blur","Shake","Zoom","Glitch","RGB","VHS","Flash","Fade","Particles","Lens"),"",{})
"Audio"->{Choices(listOf("Music","Voiceover","Sound FX","Extract audio","Fade"),"",{});Button(a,colors=ButtonDefaults.buttonColors(containerColor=Purple)){Icon(Icons.Default.Add,null);Text("Import audio")}}
"Text"->Choices(listOf("Add text","Titles","Captions","Fonts","Animation","Subtitles"),"",{})
"Elements"->Choices(listOf("Stickers","Emoji","Shapes","PIP","Overlay","Watermark","Frames","Masks"),"",{})
"Export"->{Text("Resolution",color=Color.White);Choices(listOf("480p","720p","1080p","2K","4K"),rs,srs);Text("Format",color=Color.White);Choices(listOf("MP4","MOV","WEBM","GIF"),ft,sft);Choices(listOf("24 FPS","30 FPS","60 FPS"),"",{});Button({},colors=ButtonDefaults.buttonColors(containerColor=Purple)){Icon(Icons.Default.Save,null);Text("Export video")}}
else->Choices(listOf("Canvas","Background","Reorder clips","Transitions"),"",{})}}}
@Composable private fun Choices(opts:List<String>,sel:String,cb:(String)->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){opts.forEach{Surface(color=if(it==sel)Purple else Color(0xFF252833),shape=RoundedCornerShape(8.dp),modifier=Modifier.clickable{cb(it)}){Text(it,color=Color.White,fontSize=10.sp,modifier=Modifier.padding(8.dp))}}}}
@Composable private fun Adjust(n:String){var v by remember{mutableFloatStateOf(0f)};Row(verticalAlignment=Alignment.CenterVertically){Text(n,color=Color.White,fontSize=10.sp,modifier=Modifier.width(70.dp));Slider(v,{v=it},valueRange=-100f..100f,modifier=Modifier.weight(1f));Text(v.toInt().toString(),color=Cyan,fontSize=10.sp)}}
@Composable private fun Timeline(has:Boolean){Column{Row{Text("Timeline",color=Color.White,fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));Text(if(has)"VIDEO READY" else "EMPTY",color=Color.Gray,fontSize=11.sp)};Spacer(Modifier.height(7.dp));Box(Modifier.fillMaxWidth().height(65.dp).background(Panel,RoundedCornerShape(14.dp)).border(1.dp,Color.DarkGray,RoundedCornerShape(14.dp)).padding(9.dp),contentAlignment=Alignment.Center){Text(if(has)"VIDEO CLIP" else "Import media to begin your timeline",color=if(has)Cyan else Color.Gray,fontSize=11.sp)}}}
@Composable private fun Bottom(active:String,cb:(String)->Unit){Row(Modifier.fillMaxWidth().background(Color(0xFF12141C),RoundedCornerShape(17.dp)).padding(vertical=9.dp),horizontalArrangement=Arrangement.SpaceEvenly){listOf("Edit" to Icons.Default.ContentCut,"Audio" to Icons.Default.MusicNote,"Text" to Icons.Default.TextFields,"Effects" to Icons.Default.Tune,"Export" to Icons.Default.Save).forEach{(n,i)->Column(Modifier.clickable{cb(n)},horizontalAlignment=Alignment.CenterHorizontally){Icon(i,n,tint=if(active==n)Cyan else Color.Gray);Text(n,color=if(active==n)Cyan else Color.Gray,fontSize=10.sp)}}}}
