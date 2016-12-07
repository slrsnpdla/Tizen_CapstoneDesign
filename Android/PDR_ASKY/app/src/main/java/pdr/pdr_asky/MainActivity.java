package pdr.pdr_asky;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.app.Activity;
import android.app.Service;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class Link {
    public String name;
    public int index;
    public double latitude;
    public double longitude;
    public double floor;
    public boolean isStair;
    public boolean isElevator;
    public Link nextLink; // 다음 링크를 가리키며, 초기에는 비어있다.
    public Link prevLink; // 이전 링크를 가리키며, 초기에는 비어있다.
    public ArrayList<Integer> adjLink;

    //Link data 맞춰서 선언(위도, 경도, 기압, 다음이 계단인가, 다음이 엘베인가)
    Link(String name, int index, double latitude,double longitude,
         double floor, boolean isStair, boolean isElevator, ArrayList<Integer> adjLink){
        this.name = name;
        this.index = index;
        this.latitude = latitude;
        this.longitude = longitude;
        this.floor = floor;
        this.isStair = isStair;
        this.isElevator = isElevator;
        this.adjLink = adjLink;
        this.prevLink = null;
        this.nextLink = null;
    }
}

public class MainActivity extends Activity implements SensorEventListener {

    /* Global Variables */
    EditText srcplace;// 출발지 입력
    EditText dstplace; // 도착지 입력
    Button srcsearch; // 장소 검색
    Button dstsearch; // 장소 검색
    Button button; // confirm 버튼
    ImageView mapImage; // 지도 이미지
    TextView mapName;

    TextView mygps; // gps 값 디버깅용 수치
    TextView myPressure; // 기압 디버깅용 수치
    TextView myX;
    TextView myY;
    TextView myZ;

    private SensorManager sensorManager;
    Sensor sensor;
    boolean isGPSEnabled = false;
    boolean isNetworkEnabled = false;

    // 현재 자신이 있는 곳 (Generalization을 위한 변수)
    String myBldg;
    double myPositionLatitude;
    double myPositionLongitude; // myPosition 의 위도경도에 tmp_pdr_위도경도 값을 더한 값입니다.

    float myFloorPressure; // 층 상수 + (현재기압 평균) 을 가짐.
    int myFloor; // 층 상수(constant)를 받을 변수입니다.

    /* 사용자가 optimal한 이동을 한다고 가정할 때 방향은 중요하지 않으므로 일단 구하지않도록 제거했습니다 */
    //방향을 구하기 위한 변수
    /*
    float [] myGravity;
    float [] myMag;
    float myRotationMatrix[] = new float[16];
    float myOrientation[] = new float[3];
    float myPreviousAzimuth = -1.0f;
    float myAzimuth;
    */

    // 걸음을 구하기 위한 변수
    int stepCount;
    int initCount = -1;
    int prevCount;

    // 노드 리스트를 불러오는 데 쓰이는 변수
    Reader reader;
    File sdCardFilePath;
    File textFilePath;
    String line;

    // 길안내 하는데 쓰이는 링크 변수
    Link srcPosition;
    Link dstPosition;
    Link myPosition;
    ArrayList<Link> Path;

    // 로그 찍기위해 쓰이는 변수
    BufferedWriter writer;
    File traceLogFile;

    double walking_distance; // 내가 걸은 거리
    double goal_distance; // 출발지에서 목적지까지 최단경로 거리

    double pdr_latitude;
    double pdr_longitude; // 처음 GPS 위치에서부터 지속누적한 raw pdr data 입니다.
    double tmp_pdr_latitude;
    double tmp_pdr_longitude; // partial map matching하며 초기화되는 pdr data 입니다.

    /*  상수 목록 */
    final int Eng1Floor = 100000;
    final int Eng2Floor = 200000;
    final int Eng3Floor = 300000;
    final int Eng4Floor = 400000;
    final int Eng5Floor = 500000;
    final int Eng6Floor = 600000; // 층구별 위한 상수

    // SAP 를 위한 변수
    private boolean mIsBound = false;
    private ListView mMessageListView;
    private ConsumerService mConsumerService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        srcplace = (EditText)findViewById(R.id.srcPlace);
        srcsearch = (Button)findViewById(R.id.srcSearch);
        dstplace = (EditText)findViewById(R.id.dstPlace);
        dstsearch = (Button)findViewById(R.id.dstSearch);
        button = (Button)findViewById(R.id.search_button);
        mapImage = (ImageView)findViewById(R.id.map_image);
        mapName = (TextView)findViewById(R.id.map_name);
        mygps = (TextView)findViewById(R.id.gps_value);
        myPressure = (TextView)findViewById(R.id.pressure_value);
        myX = (TextView)findViewById(R.id.position_x);
        myY = (TextView)findViewById(R.id.position_y);
        myZ = (TextView)findViewById(R.id.position_z);

        // 빠른길 찾기 버튼 콜백리스너
        button.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                if(srcplace.getText().toString().equals("") || dstplace.getText().toString().equals("")) {
                    Toast.makeText(MainActivity.this,"출발지와 도착지를 꼭 입력해주셔야 합니다.",Toast.LENGTH_SHORT).show();
                    return;
                }
                String inputText = srcplace.getText().toString() + " 에서 " + dstplace.getText().toString();
                Toast.makeText(MainActivity.this,inputText, Toast.LENGTH_SHORT).show();

                /* sdcard File IO 생성 */
                String state = Environment.getExternalStorageState();
                if(!(state.equals(Environment.MEDIA_MOUNTED))) {
                    Toast.makeText(MainActivity.this,"sdcard가 없습니다.",Toast.LENGTH_SHORT).show();
                } else {
                    reader = null;
                    try {
                        /* 건물 DB 불러오기 입출력 설정*/
                        sdCardFilePath = Environment.getExternalStorageDirectory();
                        textFilePath = new File(sdCardFilePath.getAbsolutePath()+File.separator + "bldgLink.txt");
                        FileInputStream fileStream = new FileInputStream(textFilePath);
                        reader = new InputStreamReader(fileStream, "utf-8");
                    } catch (FileNotFoundException e){
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }

                /* 지도 DB 로딩 */
                ArrayList<Link> bldg_db = new ArrayList<Link>();
                try {
                    if( reader == null ) {
                        Toast.makeText(MainActivity.this,"DB(bldgLink) 파일이 존재하지않아 DB로딩불가.",Toast.LENGTH_SHORT).show();
                        Toast.makeText(MainActivity.this,"참조하는 절대경로:"+sdCardFilePath.getAbsolutePath()+File.separator+"bldgLink",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        Thread.sleep(Math.round((Math.random()*10000)) % 2000 ,0); // 1.5초내로 빠른길 찾는 척 슬립.
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    BufferedReader in = new BufferedReader(reader);
                    while((line = in.readLine())!= null) {
                        if(new String(line.getBytes("UTF-8"),"UTF-8").contains("#")) {
                            continue; // #을 붙이면 주석이라고 인지하고 읽지않습니다.
                        }
                        String split[] = line.split(" ");
                        String tmp_name = split[0];
                        int tmp_index = Integer.valueOf(split[1]);
                        double tmp_lat = Double.valueOf(split[2]);
                        double tmp_lng = Double.valueOf(split[3]);
                        double tmp_floor = Double.valueOf(split[4]);
                        boolean tmp_stair = Boolean.valueOf(split[5]);
                        boolean tmp_elevator = Boolean.valueOf(split[6]);
                        ArrayList<Integer> tmp_adjLink = new ArrayList<Integer>();
//                        Log.d("Main","split length : "+split.length+", line:"+line);
                        for(int i = 8; i < split.length ; i++) {
                            tmp_adjLink.add(Integer.valueOf(split[i]));
                        }
                        bldg_db.add(new Link(tmp_name, tmp_index, tmp_lat, tmp_lng,
                                tmp_floor, tmp_stair, tmp_elevator, tmp_adjLink));
//                        Log.d("Main","[*]bldg_db에 링크 추가 : "+tmp_name);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if( bldg_db.size() == 0 ) {
                    Toast.makeText(MainActivity.this,"DB 정보가 없거나 손상되었습니다. 에러확인요망",Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    Toast.makeText(MainActivity.this,"****[ DB 로딩 성공 ]****",Toast.LENGTH_SHORT).show();
                }
                /* 출발점과 도착점 설정
                *  string 비교를 통해서 찾아낸다.
                * */
                Log.d("Main","출발지 명: "+srcplace.getText().toString());
                for(int i=0; i<bldg_db.size(); i++) {
                    if(srcplace.getText().toString().equals(bldg_db.get(i).name)) {
                        srcPosition = bldg_db.get(i);
                        Log.d("Main","출발지 설정 성공");
                    }
                }
                if(srcPosition == null) {
                    Toast.makeText(MainActivity.this,"출발지 설정에 실패.",Toast.LENGTH_SHORT).show();
                    return;
                }
                Log.d("Main","도착지 명: "+dstplace.getText().toString());
                for(int i=0; i<bldg_db.size(); i++) {
                    if(dstplace.getText().toString().equals(bldg_db.get(i).name)) {
                        dstPosition = bldg_db.get(i);
                        Log.d("Main","도착지 설정 성공");
                    }
                }
                if(dstPosition == null) {
                    Toast.makeText(MainActivity.this,"도착지 설정에 실패.",Toast.LENGTH_SHORT).show();
                    return;
                }
                /* 최단경로 구성
                *  층이 다른 경우 최단거리에 있는 계단에 보내주고 계단을 보낸 후 최단거리로 도착지에 보낸다.
                *  층이 같은 경우 최단거리로 도착지로 보낸다.
                * */
                if( srcPosition.floor == dstPosition.floor ) // 층이 같은 경우
                {
                    Path = new ArrayList<Link>();
                    ArrayList<Link> sameFloor = new ArrayList<Link>(); // 같은 층 Link 만 선별합니다.
                    sameFloor.add(srcPosition); // 출발지를 0번노드에 설정합니다.
                    for(Link eachLink : bldg_db) { // 출발지를 제외하고 add합니다.
                        if(eachLink.floor == srcPosition.floor && eachLink.index != srcPosition.index) {
                            sameFloor.add(eachLink);
                        }
                    }
                    if(sameFloor.size() == 0) {
                        Toast.makeText(MainActivity.this,"길안내를 위한 DB정보가 부족 또는 누락됨",Toast.LENGTH_SHORT).show();
                    }
                    /* 각 노드간 인접노드 확인 및 distance 계산해 행렬 구성 */
                    float[][] costMatrix = new float[sameFloor.size()][sameFloor.size()]; // weight Matrix임.
                    float[] shortestCost = new float[sameFloor.size()]; // i 까지 오는데에 최단경로
                    for(int i=0; i< sameFloor.size(); i++) {
                        for(int j=0; j< sameFloor.size(); j++) {
                            if (sameFloor.get(i).adjLink.contains(sameFloor.get(j).index)) {// i에서 j로 이동 가능하다면
                                costMatrix[i][j] = (float) (Math.sqrt(Math.pow(sameFloor.get(i).longitude - sameFloor.get(j).longitude, 2) +
                                        Math.pow(sameFloor.get(i).latitude - sameFloor.get(j).latitude, 2)));
                            } else {
                                costMatrix[i][j] = Float.MAX_VALUE-10000.0f; // overflow를 방지하기 위해 1만을 빼준다.
                            }
                        }
                    }


                    int[] prev = new int[sameFloor.size()];
                    /* 벨만포드 알고리즘으로 path 구성 */
                    for(int i=0; i<sameFloor.size(); i++) {
                        prev[i] = -1; // prev가 없다고 초기화.
                        if(i==0) { // srcPosition이면
                            shortestCost[i] = 0;
                        } else {
                            shortestCost[i] = Float.MAX_VALUE-10000.0f;
                        }
                    }

                    for(int i=0;i<sameFloor.size();i++) {
                        for(int j=0;j<sameFloor.size();j++) {
                            for(int k=0;k<sameFloor.size();k++) {
                                if( shortestCost[k] > shortestCost[j] + costMatrix[j][k] ) {
                                    shortestCost[k] = shortestCost[j] + costMatrix[j][k];
                                    prev[k] = j; // k의 이전노드는 j가 된다.
                                }
                            }
                        }
                    } // 벨만포드 search 끝
                    /* dstPostion 에서부터 계속 prev를 찾아서 그 position들을 Path에 넣고 reverse한다.*/
                    int dstNum = -1; // 초기화필요.
                    int srcNum = 0; // 0번이 srcNum이므로
                    for(int i=0;i<sameFloor.size();i++) {
                        if(sameFloor.get(i).index == dstPosition.index) { // i 번째 index가 도착지라면?
                            dstNum = i;
                            break;
                        }
                    }
                    Log.d("Main","도착점 링크는 : "+sameFloor.get(dstNum).name);
                    for(int i = dstNum; i != srcNum;) {
                        Path.add(sameFloor.get(i));
                        i = prev[i]; //이전 노드를 찾아서...
                        Log.d("Main","이전 링크는 : "+sameFloor.get(i).name);
                    } // dstPosition - dstPrev - dstPrevPrev - dstPrevPrevPrev - ... - srcPosition 순으로 배열에 저장되어있음.
                    Collections.reverse(Path); // 순서를 뒤집어준다.
                    // 노드간 연결 시켜주기
                    for(int i=0; i<Path.size();i++) {
                        if(Path.get(i).index == dstPosition.index) {
                            Log.d("Main",Path.get(i).name+" 은 도착지 이므로 break");
                            break;
                        }
                        /* 이전 링크와도 연결시켜야함
                        if(i > 0)
                            Path.get(i).prevLink = Path.get(i-1);
                        */
                        Path.get(i).nextLink = Path.get(i+1); // Path간 연결을 시켜준다.
                        Log.d("Main",Path.get(i).name+" 에서 다음 링크는 "+Path.get(i).nextLink.name+" 입니다.");
                    }


                } else { // 층이 다른 경우
                    /* step 1 : 같은 층에서 distMatrix로 최단거리 계단을 안내한다. */
                    Path = new ArrayList<Link>();
                    ArrayList<Link> semiPath = new ArrayList<Link>();
                    ArrayList<Link> sameFloor = new ArrayList<Link>(); // 같은 층 Link 만 선별합니다.
                    sameFloor.add(srcPosition); // 출발지를 0번노드에 설정합니다.
                    for(Link eachLink : bldg_db) { // 출발지를 제외하고 add합니다.
                        if(eachLink.floor == srcPosition.floor && eachLink.index != srcPosition.index) {
                            sameFloor.add(eachLink);
                        }
                    }
                    if(sameFloor.size() <= 1) {
                        Toast.makeText(MainActivity.this,"길안내를 위한 DB정보가 부족 또는 누락됨",Toast.LENGTH_SHORT).show();
                    }
                    /* 각 노드간 인접노드 확인 및 distance 계산해 행렬 구성 */
                    float[][] costMatrix = new float[sameFloor.size()][sameFloor.size()]; // weight Matrix임.
                    float[] shortestCost = new float[sameFloor.size()]; // i 까지 오는데에 최단경로
                    for(int i=0; i< sameFloor.size(); i++) {
                        for(int j=0; j< sameFloor.size(); j++) {
                            if (sameFloor.get(i).adjLink.contains(sameFloor.get(j).index)) {// i에서 j로 이동 가능하다면
                                costMatrix[i][j] = (float) (Math.sqrt(Math.pow(sameFloor.get(i).longitude - sameFloor.get(j).longitude, 2) +
                                        Math.pow(sameFloor.get(i).latitude - sameFloor.get(j).latitude, 2)));
                            } else {
                                costMatrix[i][j] = Float.MAX_VALUE-10000.0f; // overflow를 방지하기 위해 1만을 빼준다.
                            }
                        }
                    }

                    int[] prev = new int[sameFloor.size()];
                    /* 벨만포드 알고리즘으로 path 구성 */
                    for(int i=0; i<sameFloor.size(); i++) {
                        prev[i] = -1; // prev가 없다고 초기화.
                        if(i==0) { // srcPosition이면
                            shortestCost[i] = 0;
                        } else {
                            shortestCost[i] = Float.MAX_VALUE-10000.0f;
                        }
                    }

                    for(int i=0;i<sameFloor.size();i++) {
                        for(int j=0;j<sameFloor.size();j++) {
                            for(int k=0;k<sameFloor.size();k++) {
                                if( shortestCost[k] > shortestCost[j] + costMatrix[j][k] ) {
                                    shortestCost[k] = shortestCost[j] + costMatrix[j][k];
                                    prev[k] = j; // k의 이전노드는 j가 된다.
//                                    Log.d("Main",sameFloor.get(j).name+" 에서 "+sameFloor.get(k).name+" (으)로 연결");
                                }
                            }
                        }
                    } // 벨만포드 search 끝

                    /* dstPostion 에서부터 계속 prev를 찾아서 그 position들을 Path에 넣고 reverse한다.*/
                    float best = Float.MAX_VALUE - 100000.0f;
                    int semiDstNum = -1; // 초기화필요.
                    for(int i=0;i<sameFloor.size();i++) {
                        if(sameFloor.get(i).isStair == true || sameFloor.get(i).isElevator == true) { // i 번째 index가 계단이라면?
                            if(semiDstNum == -1 || shortestCost[semiDstNum] < best) { // 가장 가까운 계단으로 안내한다.
                                semiDstNum = i;
                                best = shortestCost[semiDstNum];
                            }
                        }
                    }

                    if(semiDstNum == -1) {
                        Log.d("Main", "semiDstNum 이 -1로 출력되어서 중단");
                        Path.clear();
                        myPosition = null;
                        srcPosition = null;
                        dstPosition = null;
                        try {
                            reader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return;
                    }

                    Log.d("Main","계단 링크 :"+sameFloor.get(semiDstNum).name);
                    for(int i = semiDstNum; i != -1;) {
                        semiPath.add(sameFloor.get(i));
                        i = prev[i]; //이전 노드를 찾아서...
                        if(i!= -1) {
                            Log.d("Main", "이번 링크: " + sameFloor.get(i).name);
                        } else {
                            Log.d("Main","이전 링크가 없네염. 에러?");
                        }
                    } // dstPosition - dstPrev - dstPrevPrev - dstPrevPrevPrev - ... - srcPosition 순으로 배열에 저장되어있음.
                    Collections.reverse(semiPath); // 순서를 뒤집어준다.
                    /* 같은 층의 계단까지 semiPath에 등록함. */

                    /* step 2 : 계단 오르내림을 시킨다. */
                    if(srcPosition.floor > dstPosition.floor) {
                        // 계단을 내려가야함
                        ArrayList<Link> stairs = new ArrayList<Link>();
                        for(Link eachLink : bldg_db) {
                            if(eachLink.isStair == true && ((eachLink.index % 100) == (semiPath.get(semiPath.size()-1).index % 100))) {
                                Log.d("Main","eachLink.index:"+eachLink.index+" 내 현재 계단인덱스:"+semiPath.get(semiPath.size()-1).index);
                                stairs.add(eachLink);
                            } // 같은 쪽에 있는 계단을 모두 불러옵니다.
                        }
                        if(stairs.size() <= 1) {
                            Toast.makeText(MainActivity.this,"길안내를 위한 DB정보가 부족 또는 누락됨",Toast.LENGTH_SHORT).show();
                        }

                        double curr_floor = srcPosition.floor;
                        for(int i=0; i< stairs.size(); i++) {
                            if((int)curr_floor - 100000 == (int)stairs.get(i).floor) { // 100000.0f 는 한 층 차이를 의미
                                semiPath.add(stairs.get(i)); //한층 내려간 거 경로에 등록
                                Log.d("Main", stairs.get(i).name + "계단을 등록합니다.");
                                curr_floor = stairs.get(i).floor;
                                i = -1; // i++을 무효화시키며 처음부터 다시 search
                                if(curr_floor == dstPosition.floor) {
                                    break;
                                }
                            }
                        }
                        // 계단 내려오기 끝

                    } else {
                        // 같은 층이지 않으므로, 계단을 올라가는 경우임
                        ArrayList<Link> stairs = new ArrayList<Link>();
                        for(Link eachLink : bldg_db) {
                            if(eachLink.isStair == true && ((eachLink.index % 100) == (semiPath.get(semiPath.size()-1).index % 100))) {
                                stairs.add(eachLink);
                            } // 같은 쪽에 있는 계단을 모두 불러옵니다.
                        }
                        if(stairs.size() <= 1) {
                            Toast.makeText(MainActivity.this,"길안내를 위한 DB정보가 부족 또는 누락됨",Toast.LENGTH_SHORT).show();
                        }

                        double curr_floor = srcPosition.floor;
                        for(int i=0; i< stairs.size(); i++) {
                            if((int)curr_floor + 100000 == (int)stairs.get(i).floor) {
                                semiPath.add(stairs.get(i)); // 한층 올라간 경로 등록
                                Log.d("Main", stairs.get(i).name + "계단을 등록합니다.");
                                curr_floor = stairs.get(i).floor;
                                i = -1; // 처음부터 다시 search
                                if(curr_floor == dstPosition.floor) {
                                    break;
                                }
                            }
                        }
                        // 계단 올라가기 끝
                    }

                    Path.addAll(semiPath);
                    semiPath.clear(); // 여태까지 경로를 Path 에 옮겨놓고 semiPath에 나머지경로를 만들어서 이어붙인다.

                    /* step 3 : 같은 층에서 costMatrix로 최단거리 목적지를 안내한다. */
                    sameFloor.clear();
                    sameFloor.add(Path.get(Path.size()-1)); // 출발지를 0번에 등록한다.
                    for(Link eachLink : bldg_db) { // 출발지를 제외하고 add합니다.
                        if(eachLink.floor == sameFloor.get(0).floor && eachLink.index != sameFloor.get(0).index) {
                            sameFloor.add(eachLink);
                        }
                    }
                    if(sameFloor.size() <= 1) {
                        Toast.makeText(MainActivity.this,"길안내를 위한 DB정보가 부족 또는 누락됨",Toast.LENGTH_SHORT).show();
                    }
                    /* 각 노드간 인접노드 확인 및 distance 계산해 행렬 구성 */
                    costMatrix = new float[sameFloor.size()][sameFloor.size()]; // weight Matrix임.
                    shortestCost = new float[sameFloor.size()]; // i 까지 오는데에 최단경로
                    for(int i=0; i< sameFloor.size(); i++) {
                        for(int j=0; j< sameFloor.size(); j++) {
                            if (sameFloor.get(i).adjLink.contains(sameFloor.get(j).index)) {// i에서 j로 이동 가능하다면
                                costMatrix[i][j] = (float) (Math.sqrt(Math.pow(sameFloor.get(i).longitude - sameFloor.get(j).longitude, 2) +
                                        Math.pow(sameFloor.get(i).latitude - sameFloor.get(j).latitude, 2)));
                            } else {
                                costMatrix[i][j] = Float.MAX_VALUE-10000.0f; // overflow를 방지하기 위해 1만을 빼준다.
                            }
                        }
                    }

                    prev = new int[sameFloor.size()];
                    /* 벨만포드 알고리즘으로 path 구성 */
                    for(int i=0; i<sameFloor.size(); i++) {
                        prev[i] = -1; // prev가 없다고 초기화.
                        if(i==0) { // srcPosition이면
                            shortestCost[i] = 0;
                        } else {
                            shortestCost[i] = Float.MAX_VALUE-10000.0f;
                        }
                    }
                    for(int i=0;i<sameFloor.size();i++) {
                        for(int j=0;j<sameFloor.size();j++) {
                            for(int k=0;k<sameFloor.size();k++) {
                                if( shortestCost[k] > shortestCost[j] + costMatrix[j][k] ) {
                                    shortestCost[k] = shortestCost[j] + costMatrix[j][k];
                                    prev[k] = j; // k의 이전노드는 j가 된다.
//                                    Log.d("Main",sameFloor.get(j).name+" 에서 "+sameFloor.get(k).name+" 을 연결");
                                }
                            }
                        }
                    } // 벨만포드 search 끝
                    /* dstPostion 에서부터 계속 prev를 찾아서 그 position들을 Path에 넣고 reverse한다.*/
                    int dstNum = -1; // 초기화필요.
                    for(int i=1;i<sameFloor.size();i++) {
                        if(sameFloor.get(i).index == dstPosition.index) { // i 번째 index가 도착지라면?
                            dstNum = i;
//                            Log.d("Main","dstNum : "+dstNum);
                            break;
                        }
                    }
                    for(int i = dstNum; i != 0;) { // 0은 출발지 번호를 의미함.
                        semiPath.add(sameFloor.get(i));
                        i = prev[i]; //이전 노드를 찾아서...
                    } // dstPosition - dstPrev - dstPrevPrev - dstPrevPrevPrev - ... - srcPosition 순으로 배열에 저장되어있음.
                    Collections.reverse(semiPath); // 순서를 뒤집어준다.

                    Path.addAll(semiPath); // 완전경로 완성
                    semiPath.clear();
                    Log.d("Main","출발지 링크는 :"+Path.get(0).name);
                    for(int i=0; i<Path.size(); i++) {
                        if (Path.get(i).index == dstPosition.index) {
                            Log.d("Main",Path.get(i).name+" 은 도착지 이므로 break");
                            // 도착지라면
                            break;
                        }
                        Path.get(i).nextLink = Path.get(i + 1); // Link간 연결을 시켜준다.
                        Log.d("Main","다음 링크는 :"+Path.get(i+1).name);
                    }
                }

                /* 벨만포드 최단경로 알고리즘을 통해 Path 객체애 모든 경로를 순서대로 넣는 게 성공. */
                Toast.makeText(MainActivity.this,"빠른 경로 찾기 완료", Toast.LENGTH_SHORT).show();
                myPosition = srcPosition;
                myX.setText("[PDR]latitude: "+pdr_latitude+" longitude:"+pdr_longitude);
                myZ.setText("현재 위치: "+myPosition.name);
                /* Null object reference 에러를 방지하기위해 넣은 if 문 입니다. */
                if(myPosition != null) {
                    mapName.setText("제1공학관 "+(int)(myPosition.floor)/100000+"층");
                    switch(myFloor) {
                        case Eng1Floor:
                            mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_1f));
                            break;
                        case Eng2Floor:
                            mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_2f));
                            break;
                        case Eng3Floor:
                            mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_3f));
                            break;
                        case Eng4Floor:
                            mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_4f));
                            break;
                        case Eng5Floor:
                            mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_5f));
                            break;
                        case Eng6Floor:
                            mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_6f));
                            break;
                        default:
                    }
                }
                /* 걸음 자취 찍기 입출력 설정*/
                try {
                    traceLogFile = new File(sdCardFilePath.getAbsolutePath()+File.separator + "pdrLog.txt");
                    writer = new BufferedWriter(new FileWriter(traceLogFile,true),1024);
                    writer.newLine();
                    writer.append("Guide Start");
                } catch (IOException e) {
                    Toast.makeText(MainActivity.this,"pdrLog.txt 불러오지 못했습니다.",Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }});

        // 출발지 찾기 버튼 콜백리스너
        srcsearch.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                String src = srcplace.getText().toString();
                Toast.makeText(MainActivity.this, src+" 를 검색...", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                Bundle b = new Bundle();
                b.putString("targetPlace", src);
                intent.putExtras(b);
                startActivityForResult(intent, 0);
            }
        });
        // 도착지 찾기 버튼 콜백리스너
        dstsearch.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                String dst = dstplace.getText().toString();
                Toast.makeText(MainActivity.this, dst+" 를 검색...", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                Bundle b = new Bundle();
                b.putString("targetPlace", dst);
                intent.putExtras(b);
                startActivityForResult(intent, 1);
            }
        });

        //create instance of sensor manager and get system service to interact with Sensor
        sensorManager = (SensorManager) getSystemService(Service.SENSOR_SERVICE);
        // Acquire a reference to the system Location Manager
        final LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        // GPS 프로바이더 사용가능여부
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 네트워크 프로바이더 사용가능여부
        isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        Log.d("Main", "isGPSEnabled="+ isGPSEnabled);
        Log.d("Main", "isNetworkEnabled="+ isNetworkEnabled);

        final LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                if(pdr_latitude == 0 || pdr_longitude == 0) {
                    // 처음 한번만 GPS 값을 불러온다.
                    Toast.makeText(MainActivity.this,"초기 GPS값 로딩",Toast.LENGTH_SHORT).show();
                    pdr_latitude =  Math.round(location.getLatitude()*1000000)/1000000.0f;
                    pdr_longitude = Math.round(location.getLongitude()*1000000)/1000000.0f;
                    myX.setText("[PDR]latitude: "+pdr_latitude+" longitude:"+pdr_longitude);
                }
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {
                mygps.setText("onStatusChanged");
//                Toast.makeText(MainActivity.this,"onStatusChanged",Toast.LENGTH_SHORT).show();
            }
            public void onProviderEnabled(String provider) {
                mygps.setText("onProviderEnabled");
//                Toast.makeText(MainActivity.this,"onProviderEnabled",Toast.LENGTH_SHORT).show();
            }

            public void onProviderDisabled(String provider) {
                mygps.setText("onProviderDisabled");
//                Toast.makeText(MainActivity.this,"onStatusDisabled",Toast.LENGTH_SHORT).show();
            }
        };
        // Register the listener with the Location Manager to receive location updates
        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission( getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission( getApplicationContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(MainActivity.this,"앱에 위치정보 권한을 주셔야합니다.",Toast.LENGTH_SHORT).show();
            return ;
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent) ;
        switch(requestCode) {
            case 0: // srcSearch Result
                if(resultCode == RESULT_OK) {
                    Bundle b = intent.getExtras(); // 받아온 Extras를 활용하면 된다.
                    srcplace.setText(b.getString("targetPlace"));
                }
                break;
            case 1: // dstSearch Result
                if(resultCode == RESULT_OK) {
                    Bundle b = intent.getExtras();
                    dstplace.setText(b.getString("targetPlace"));
                }
                break;
            default:
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        // register this class as a listener
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE),
                SensorManager.SENSOR_DELAY_NORMAL);//기압계 센서 등록. 가장 느리게 센서 체크
/* 방향각 측정은 공학관의 경우 주변 지자기 간섭요소가 너무 많아 생략합니다. 추후 논문참고에 의해 수정될 여지도 있습니다. */
/*
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
                SensorManager.SENSOR_DELAY_UI);//중력 센서 등록(이를 통해 방향각을 정할 예정)
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_UI);//자기장 센서 등록(이를 통해 방향각을 정할 예정)
*/
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
                SensorManager.SENSOR_DELAY_FASTEST);//걸음수 센서 등록

    }

    // called when sensor value have changed
    @Override
    public void onSensorChanged(SensorEvent event) {
        /***********************
         *  기압               *
         ***********************/
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            float[] values = event.values;
            float value;
            value = Math.round(values[0] * 10) / 10.0f;//유효숫자정리  V
            if (myFloor > 0) {
                myFloorPressure = myFloor + ((myFloorPressure - myFloor) * 0.98f + value * 0.02f);
                // 현재 층기압 = 현재 층 + (이전기압*0.98+현재기압*0.02)/2
                // 급격한 평균 변동을 막기 위해서 가중치를 둡니다.
                myPressure.setText("" + myFloorPressure);
            } else {
                myFloor = Eng3Floor;
                myFloorPressure = myFloor + value;
                mapName.setText("[*기본이미지입니다*]");
                mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_3f));
            }
            /* 층간 기압은 일정하게 0.4씩 차이난다.
            * 경계는 진동이 일어날 수 있으므로 변화를 주지 않는다.
            * 실측정 기압이 날씨의 변화와 대기상태에 따라 층구별이 힘들만큼 차이가 날 수 있어서 커스텀값과 이동값을 이용해서 계산해야 할 듯 함.
            * ex : 1층 100000 ,2층 200000, 3층 300000... 로 두고, 0.4의 기압차(계단오르내림)의 경우 층 변화로 인식.
            */
            if (myFloorPressure - (float) myFloor - value <= 0.4f &&
                    myFloorPressure - (float) myFloor - value >= -0.4f) {  // 층변화가 없는 경우
                ;
            } else if (myFloorPressure - (float) myFloor - value >= +0.4) { // 계단 올라감
                if(Path != null && Path.size() >= 1 && myPosition.isStair == true && myPosition.nextLink.isStair == true &&
                        ( (int)myPosition.nextLink.floor - (int)myPosition.floor == 100000 )) {
                    // 다음 링크가 한 층 위로 올라가는 것이고 층 변화가 있는 경우에 myPosition 변화
                    /*
                    *  여기에 한 층 내려갔다 올라온 케이스에 대해 예외처리 안했지만, 그런 ㅄ은 없을거라고 가정하고 작성한 코드임
                    * */
                    myPosition = myPosition.nextLink;
                }
                switch (myFloor) {
                    case Eng1Floor:
                        myFloor = Eng2Floor; // upstair
                        myPressure.setText("" + myFloorPressure);
                        mapName.setText("제1공학관 2층");
                        mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_2f));
                        break;
                    case Eng2Floor:
                        myFloor = Eng3Floor; // upstair
                        mapName.setText("제1공학관 3층");
                        mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_3f));
                        break;
                    case Eng3Floor:
                        myFloor = Eng4Floor; // upstair
                        mapName.setText("제1공학관 4층");
                        mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_4f));
                        break;
                    case Eng4Floor:
                        myFloor = Eng5Floor; // upstair
                        mapName.setText("제1공학관 5층");
                        mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_5f));
                        break;
                    case Eng5Floor:
                        myFloor = Eng6Floor; // upstair
                        mapName.setText("제1공학관 6층");
                        mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_6f));
                        break;
                    default:
                }
                myFloorPressure = myFloor + value;
            } else if (myFloorPressure - (float) myFloor - value <= -0.4) {// 계단 내려감.
                if(Path.size() >= 1 && myPosition.isStair == true && myPosition.nextLink.isStair == true &&
                        ( (int)myPosition.nextLink.floor - (int)myPosition.floor == -100000 )) {
                    // 다음 링크가 한 층 아래로 내려가는 것이고 층 변화가 있는 경우에 myPosition 변화
                    myPosition = myPosition.nextLink;
                    /*
                    *  여기에 한 층 올라갔다 내려온 케이스에 대해 예외처리 안했지만, 그런 일은 없을거라고 가정하고 작성한 코드임
                    * */
                }
                switch (myFloor) {
                    case Eng2Floor:
                        myFloor = Eng1Floor; // downstair
                        mapName.setText("제1공학관 1층");
                        mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_1f));
                        break;
                    case Eng3Floor:
                        myFloor = Eng2Floor; // downstair
                        mapName.setText("제1공학관 2층");
                        mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_2f));
                        break;
                    case Eng4Floor:
                        myFloor = Eng3Floor; // downstair
                        mapName.setText("제1공학관 3층");
                        mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_3f));
                        break;
                    case Eng5Floor:
                        myFloor = Eng4Floor; // downstair
                        mapName.setText("제1공학관 4층");
                        mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_4f));
                        break;
                    case Eng6Floor:
                        myFloor = Eng5Floor; // downstair
                        mapName.setText("제1공학관 5층");
                        mapImage.setImageDrawable(getResources().getDrawable(R.drawable.eng_a_5f));
                        break;
                    default:
                }
                myFloorPressure = myFloor + value;
            }
        }
        /***********************
         *  걸음 검출          *
         ***********************/
        else if(event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            String str;
            if(initCount == -1) {
                initCount = (int) event.values[0];
                Toast.makeText(MainActivity.this,"Stepcount 초기화",Toast.LENGTH_SHORT).show();
            } else {
                /*
                * 2016-05-10 13:27 확인해봐야할 요소.
                * 걸음이 6~10걸음이 한번에 오르는 모습을 볼 수 있는데, 이 때 센서가 6~10번 발생하는지 한 번만 발생하는지 검증해봐야 한다.
                * */
                stepCount = ((int) event.values[0]) - initCount;
                if(stepCount < 0) {
                    stepCount = 0;
                    prevCount = 0;
                }
                if(stepCount > prevCount) { // 걸음 검출
                    /*
                    * 일반 인간의 걸음을 70cm(0.7m)라고 가정한다.
                    * 그리고 0.00001 당 latitude 1.1m, longitude 0.9m 라고 가정하고 환산한다.
                    */
                    if(myPosition != null && myPosition.nextLink != null &&
                            myPosition.isStair == true && myPosition.nextLink.isStair == true) {
                        // 계단을 오르고 있는 경우에는 PDR 변화를 주지 않고 기압변화만 확인한다.
                        myX.setText("계단을 올라주세요");
                    } else {
                        /* 원래 Azimuth를 raw data로 넣으려고 했지만, ideal state의 angle을 Azimuth로 임의계산해서 대입한다. */
                        double shortestCost = Math.sqrt( Math.pow(myPosition.nextLink.latitude - myPosition.latitude,2) + Math.pow(myPosition.nextLink.longitude - myPosition.longitude,2) );
                        double tmp_cos = (myPosition.nextLink.longitude - myPosition.longitude) / shortestCost ;
                        double tmp_sin = (myPosition.nextLink.latitude - myPosition.latitude) / shortestCost;

                        tmp_pdr_latitude += 0.7 * tmp_sin / 1.1f / 100000.0f;
                        tmp_pdr_longitude += 0.7 * tmp_cos / 0.9f / 100000.0f;
                        pdr_latitude += 0.7 * tmp_sin / 1.1f / 100000.0f;
                        pdr_longitude += 0.7 * tmp_cos / 0.9f / 100000.0f;

                        pdr_latitude = Math.round(pdr_latitude*1000000)/1000000.0f;
                        pdr_longitude = Math.round(pdr_longitude*1000000)/1000000.0f;
/*
                        myPositionLatitude = myPosition.latitude + tmp_pdr_latitude;
                        myPositionLongitude = myPosition.longitude + tmp_pdr_longitude;
*/
                        try {
                            if(writer != null) {
                                writer.append("[PDR] latitude: "+(float)pdr_latitude+" longitude: "+(float)pdr_longitude);
                                writer.newLine();
                                writer.append("[MM/"+event.timestamp+"] "+Math.round(myPositionLatitude*1000000)/1000000.0f+" "+Math.round(myPositionLongitude*1000000)/1000000.0f);
                                writer.newLine();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        if(myPosition != null && myPosition.nextLink != null) {
                            mygps.setText("[MM남은]latitude: " + (Math.round(myPosition.nextLink.latitude * 1000000) - Math.round(myPositionLatitude * 1000000))
                                    + ", longitude: " + (Math.round(myPosition.nextLink.longitude * 1000000) - Math.round(myPositionLongitude * 1000000)));
                            myX.setText("[PDR]latitude: "+pdr_latitude+" longitude: "+pdr_longitude);
                        }
                    }
                    prevCount = stepCount;
                    // 걸음을 계산해서 이동한 위치를 추정한다.

                    if(Path.size() > 1 && myPosition != null) { // 빠른 길찾기 중 걸음 확인. Navigate 함수 실행
                        if(Navigate(srcPosition,dstPosition) == true) {
                            Toast.makeText(MainActivity.this,"길안내 끝. 목적지에 도착했습니다.",Toast.LENGTH_SHORT).show();
                            myPosition = null;
                            Path.clear();

                            try {
                                if(writer != null) {
                                    writer.append("Guide Complete.");
                                    writer.newLine();
                                    writer.close();
                                }
                            } catch(IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                }
            }
            str = "step : " + stepCount + " 걸음";
            myY.setText(str);
        }
        /* 방향을 센서를 통해 계산하는 부분은 공학관 내 지자기 간섭요소가 너무 많아 논문참고하지 않는 이상 삭제해둡니다. */
        /*
        else if(event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            myGravity = event.values.clone();
        } else if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            myMag = event.values.clone();
        }
        */
        /***********************
         *  방향               *
         ***********************/
        /*
        if(myGravity != null && myMag != null)
        {
            // 해당 알고리즘은 방향센서에서 azimuth를 정확하게 얻기 위한 권장사항 코드임.
            SensorManager.getRotationMatrix(myRotationMatrix, null, myGravity, myMag);

            //    SensorManager.getOrientation 은 구글이 쓰레기같이 만들어놔서 정확한 방향을 뱉지 않는다.
            //    따라서 수학적으로 정밀한 방향을 얻기 위해 새 함수를 고안한다.
            //    이 함수는 http://stackoverflow.com/questions/15537125/inconsistent-orientation-sensor-values-on-android-for-azimuth-yaw-and-roll
            //    다음의 링크를 참조하여 작성하였다.
            //    주의사항은, 주변에 자기장을 형성하는 요인이 있을 경우 지자기센서가 올바른 값을 뱉지 못함.
            //    따라서 자기장을 형성하는 요인을 최대한 피해야 길안내가 잘 된다.
            //
            if(myRotationMatrix.length == 16) {
                myOrientation[0] = (float)Math.atan2(myRotationMatrix[1],myRotationMatrix[5]);
                myOrientation[1] = (float)Math.asin(-myRotationMatrix[9]);
                myOrientation[2] = (float)Math.atan2(-myRotationMatrix[8],myRotationMatrix[10]);
            } else if(myRotationMatrix.length == 9) {
                myOrientation[0] = (float)Math.atan2(myRotationMatrix[1], myRotationMatrix[4]);
                myOrientation[1] = (float)Math.asin(-myRotationMatrix[7]);
                myOrientation[2] = (float)Math.atan2(-myRotationMatrix[6],myRotationMatrix[8]);
            }
            myAzimuth = ((int) Math.toDegrees(myOrientation[0])); // azimuth Radian to Degree.
            float tmp_mypitch = (float) Math.toDegrees(myOrientation[1]);
            float tmp_myroll = (float) Math.toDegrees(myOrientation[2]);
            if (tmp_mypitch < -10.0f || tmp_mypitch > 10.0f)
                return; // azimuth 정밀성을 방해하는 값이므로 평균에 넣지 않는다.
            if (tmp_myroll < -10.0f || tmp_myroll > 10.0f)
                return; // 위와 동일.
            if(myAzimuth < 0) {
                myAzimuth = 360 + myAzimuth;
            }
            if(myPreviousAzimuth == -1) {
                myPreviousAzimuth = myAzimuth;
            } else {
                myPreviousAzimuth = (myAzimuth*0.4f + myPreviousAzimuth*0.6f);
                // 이전 방위각의 영향을 받으면서 현재 방위각을 갱신한다.
            }
            myZ.setText("방위각(azimuth) : " + (int)myAzimuth);
        }
        */
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
//        Toast.makeText(MainActivity.this, "AccuracyChanged "+accuracy, Toast.LENGTH_SHORT).show();
    }
    @Override
    protected void onPause() {
        // unregister listener
        super.onPause();
        sensorManager.unregisterListener(this); // 재실행시 절대방향 초기화를 위함.
    }

    @Override
    protected void onStop() {
        super.onStop();
        sensorManager.unregisterListener(this); // 종료시 절대방향 초기화를 위함.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        /* 애플리케이션 종료시 파일입출력 중단 */
        if(reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /* 층 변화 관련 PartialNavigate는 그냥 센서에서 처리해버렸음 여기는 그냥 층 내 이동만 체크 */
    boolean Navigate(Link srcPosition, Link dstPosition)
    {
        if(srcPosition != myPosition) // if we already move many virtual link path...
            return Navigate(srcPosition.nextLink, dstPosition);
        else
        {
            // coding section. we will use partialNavigate
            if(myPosition != dstPosition)
            {
                if(partialNavigate(myPosition, myPosition.nextLink)) // if we advance a link, move to next
                {
                    // move myPosition to next.
                    myPosition = myPosition.nextLink;
                    myZ.setText("현재 위치: "+myPosition.name);

                    // 이전노드, 현재노드, 다음노드에 대해 v1, v2 벡터를 만들고
                    // v1 벡터를 기준벡터로 잡아 (a,b)에 대해 (b,-a) 법선벡터 v1' 를 만든다.
                    // v1' * v2 (dot product) > 0 이면 오른쪽이동, < 0 이면 왼쪽이동.
                    // | (v1' * v2) | <= threshold 이면 Go Straight 이다.
                    // 타이젠 기어측으로 SAP를 통해 전송한다.
                    // 일단 제출버전은 Go Straight 만 나오게 SAP 전송.
                    mConsumerService.findPeers();
                    /*
                    // Vector Class 만들고, * operator 연산자 오버로딩해서 내적 함수 추가한다
                    Vector v1 = new Vector( ( myPosition.latitude - myPosition.prevLink.latitude ) , ( myPosition.longitude - myPosition.prevLink.longitude) );
                    Vector v2 = new Vector( ( myPosition.nextLink.latitude - myPosition.latitude ) , ( myPosition.nextLink.longitude, myPosition.longitude) );
                    Vector _v1 = new Vector( v1.y, -v1.x );
                    float threshold_val = 3.0; // 임의로 설정할 것.
                    float direction_calculated = Math.abs( _v1 * v2 );
                    if( direction_calculated <= threshold_val ) {
                        mConsumerService.sendData("Go Straight");
                    } else if( direction_calculated > 0 ) {
                        mConsumerService.sendData("Turn Right");
                    } else if( direction_calculated < 0 ) {
                        mConsumerService.sendData("Turn Left");
                    }
                     */
                    mConsumerService.sendData("Go Straight");
                    mConsumerService.closeConnection();
                }
                return false;
            }
            else
                return true; // we arrive dstPosition? then, we return TRUE
        }
    }

    boolean partialNavigate(Link onPosition, Link nextPosition)
    {
        // we assume that pedestrian should be followed this guidance.

        // latitude matching
        if(onPosition.latitude < nextPosition.latitude)
        { // if I should go to (+) direction
            if( onPosition.latitude + tmp_pdr_latitude > nextPosition.latitude ) {
                Toast.makeText(MainActivity.this,"초과 이동으로 보정합니다.",Toast.LENGTH_SHORT).show();
                tmp_pdr_latitude = nextPosition.latitude - onPosition.latitude;
            }

            // 2번째 예외. azimuth 오차로 인해 (+) 방향으로 가야하는데, (-) 방향으로 간다고 인지하는 경우.
            if( tmp_pdr_latitude < 0 ) {
                Toast.makeText(MainActivity.this,"latitude (+) 방향으로 이동해주세요.",Toast.LENGTH_SHORT).show();
                // 이런 경우가 발생하는지는 아직 잘 모르겠음.
            }
        }
        else if(onPosition.latitude > nextPosition.latitude)
        { // else I should go to (-) direction
            if( onPosition.latitude + tmp_pdr_latitude < nextPosition.latitude ) {
                Toast.makeText(MainActivity.this,"초과 이동으로 보정합니다.",Toast.LENGTH_SHORT).show();
                tmp_pdr_latitude = nextPosition.latitude - onPosition.latitude;
            }

            if( tmp_pdr_latitude > 0 ) {
                Toast.makeText(MainActivity.this,"latitude (-) 방향으로 이동해주세요.",Toast.LENGTH_SHORT).show();
                // 이런 경우가 발생하는지는 아직 잘 모르겠음.
            }
        }
        else // I go to (0) direction
            tmp_pdr_latitude = 0;

        // longitude matching
        if(onPosition.longitude < nextPosition.latitude)
        { // if I go to (+) direction
            if(onPosition.longitude + tmp_pdr_longitude > nextPosition.longitude) {
                Toast.makeText(MainActivity.this,"초과 이동으로 보정합니다.",Toast.LENGTH_SHORT).show();
                tmp_pdr_longitude = nextPosition.longitude - onPosition.longitude;
            }

            if( tmp_pdr_longitude < 0 ) {
                Toast.makeText(MainActivity.this,"longitude (+) 방향으로 이동해주세요.",Toast.LENGTH_SHORT).show();
            }

        }
        else if(onPosition.longitude > nextPosition.longitude)
        { // else I go to (-) direction
            if(onPosition.longitude + tmp_pdr_longitude < nextPosition.longitude) {
                Toast.makeText(MainActivity.this,"초과 이동으로 보정합니다.",Toast.LENGTH_SHORT).show();
                tmp_pdr_longitude = nextPosition.longitude - onPosition.longitude;
            }

            if( tmp_pdr_longitude > 0 ) {
                Toast.makeText(MainActivity.this,"longitude (-) 방향으로 이동해주세요.",Toast.LENGTH_SHORT).show();
                // 이런 경우가 발생하는지는 아직 잘 모르겠음.
            }
        }
        else // I go to (0) direction
            tmp_pdr_longitude = 0;

        myPositionLatitude = onPosition.latitude + tmp_pdr_latitude; // 보정된 myPositionLatitude
        myPositionLongitude = onPosition.longitude + tmp_pdr_longitude; // 보정된 myPositionLongitude

        if(Math.round(myPositionLatitude*100000) == Math.round(nextPosition.latitude*100000) && Math.round(myPositionLongitude*100000) == Math.round(nextPosition.longitude*100000) ) {
            tmp_pdr_latitude = 0;
            tmp_pdr_longitude= 0;
            Toast.makeText(MainActivity.this, myPosition.name+" 링크에 도착 성공",Toast.LENGTH_SHORT).show();
            return true;
        } else {
            return false;
        }

    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mConsumerService = ((ConsumerService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mConsumerService = null;
            mIsBound = false;
        }
    };


    private static final class Message {
        String data;

        public Message(String data) {
            super();
            this.data = data;
        }
    }
}
