package pdr.pdr_asky;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

public class SearchActivity extends AppCompatActivity {

    private ListView m_ListView;
    private ArrayAdapter<String> m_Adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        /*
        *  검색들어온 문자에 대해 필터링하고싶다면
        *  여기서 add 하기 전에 string에 대해 필터링 시작하셈.
        *  DB에 string도 저장하고 있으니 하기 쉬울것임.
        * */
        ArrayList<String> linkInfo = new ArrayList<String>();
        linkInfo.add("제1공학관로비");
        linkInfo.add("제1공학관108호");
        linkInfo.add("제1공학관305호(컴퓨터실)");
        linkInfo.add("제1공학관373호");
        linkInfo.add("제1공학관528호");
        linkInfo.add("제1공학관525호(이경우교수님)");
        linkInfo.add("제1공학관5층남자화장실1");
        linkInfo.add("제1공학관5층남자화장실2");
        linkInfo.add("제1공학관5층여자화장실1");
        linkInfo.add("제1공학관5층여자화장실2");

        // Android에서 제공하는 string 문자열 하나를 출력 가능한 layout으로 어댑터 생성
        m_Adapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1,linkInfo);
        // Xml에서 추가한 ListView 연결
        m_ListView = (ListView)findViewById(R.id.listview);
        // ListView에 어댑터 연결
        m_ListView.setAdapter(m_Adapter);
        // ListView 아이템 선택 시 이벤트
        m_ListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
                // 이벤트 발생 시 해당 아이템 위치의 텍스트를 출력
                Bundle extra = new Bundle();
                Intent intent = new Intent();
                extra.putString("targetPlace", m_Adapter.getItem(index).toString() );
                intent.putExtras(extra); // 반환 내용들을 <key, value> 쌍으로 전송한다. 액티비티간 파라미터교환 규약으로 이러한 방식이 사용된다.
                setResult(RESULT_OK, intent); // 해당 액티비티 사용 후 반환 intent에 어떤 결과를 보낼지 설정한다.
                finish(); // 액티비티 종료문구. C언어로 보면 return setResult(); 과 비슷하다.
                // Toast.makeText(getApplicationContext(), m_Adapter.getItem(arg2), Toast.LENGTH_SHORT).show(); // 디버깅용 출력문구.
            }
        });


    }

}
