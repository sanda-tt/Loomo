package com.xs.ai.loomodemo.wander;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.xs.ai.loomodemo.R;
import com.xs.ai.loomodemo.coco.CocoClassName;
import com.xs.ai.loomodemo.segwayservice.SegwayService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

class CocoNamesListViewAdapter extends RecyclerView.Adapter<CocoNamesListViewAdapter.ViewHolder> {
    private Context context;
    private String[] listName;
    private View inflater;
    private int selectedPosition = -1;

    public CocoNamesListViewAdapter(Context context){
        this.context = context;
    }

    public void setData(final String[] listName) {
        this.listName = listName;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        //创建ViewHolder，返回每一项的布局
        // shut down by SC -1
        inflater = LayoutInflater.from(context).inflate(R.layout.face_list_item, viewGroup, false);
        ViewHolder myViewHolder = new ViewHolder(inflater);
        return myViewHolder;
    }

    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {  //定义接口，实现Recyclerview点击事件
        void OnItemClick(View view, ViewHolder holder, int position);
    }


    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {   //实现点击
        this.onItemClickListener = onItemClickListener;
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder viewHolder, final int i) {
        //将数据和控件绑定
        viewHolder.setData(listName[i]);

        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onItemClickListener.OnItemClick(view, viewHolder, viewHolder.getAdapterPosition());
                selectedPosition = i; //选择的position赋值给参数
                notifyItemChanged(selectedPosition);//刷新当前点击item
            }
        });
    }

    @Override
    public int getItemCount() {
        //返回Item总条数
        return listName.length;
    }

    private void setCurSel(int i) {

    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView textView;
        private String name;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // shut down by SC -1
            textView = (TextView) itemView.findViewById(R.id.text_view);
            textView.setBackgroundColor(Color.rgb(0x00, 0x82, 0xe8));
            textView.setTextColor(Color.WHITE);
        }

        public void setData(final String _name) {
            name = _name;
            textView.setText(name);
        }

        public String getName() {
            return name;
        }
    }
}

public class ManualReviseDetectResult extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_revise_detect_result);

        // 全屏
        Objects.requireNonNull(getActionBar()).hide();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        ((EditText)findViewById(R.id.input_name)).setInputType(InputType.TYPE_CLASS_TEXT); // 单行

        findViewById(R.id.btn_ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String str = ((EditText)findViewById(R.id.input_name)).getText().toString().trim().toLowerCase();
                if (str.isEmpty()) {
                    SegwayService.speak("Please select or input type.");
                    return;
                }
                Intent data = new Intent();
                data.putExtra("name", str);
                setResult(RESULT_OK, data);
                finish();
            }
        });

        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.coco_names_list);
        CocoNamesListViewAdapter adapterDome = new CocoNamesListViewAdapter(ManualReviseDetectResult.this);
        adapterDome.setOnItemClickListener(new CocoNamesListViewAdapter.OnItemClickListener() {
            @Override
            public void OnItemClick(View view, CocoNamesListViewAdapter.ViewHolder holder, int position) {
                String curName = holder.getName();
                ((EditText)findViewById(R.id.input_name)).setText(curName);
            }
        });

        LinearLayoutManager manager = new LinearLayoutManager(ManualReviseDetectResult.this);
        manager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(manager);
        recyclerView.setAdapter(adapterDome);

        // 按字母顺序排序显示，要不然都不好找
        String[] lst = CocoClassName.allNames80();
        Arrays.sort(lst);
        adapterDome.setData(lst);
    }

    @Override
    protected void onResume() {
        super.onResume();

        ((EditText)findViewById(R.id.input_name)).setText("");
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
