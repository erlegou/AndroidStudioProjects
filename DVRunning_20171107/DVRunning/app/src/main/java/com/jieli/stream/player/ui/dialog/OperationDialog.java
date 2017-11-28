package com.jieli.stream.player.ui.dialog;

import android.app.DialogFragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.jieli.stream.player.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class OperationDialog extends DialogFragment {

    private ListView mListView;
    private static final String OPERATION = "operation";
    private OnOperationCompleteListener mOnOperationCompleteListener;
    private String mSelectedFileName;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.operation_dialog, container, false);
        getDialog().setTitle(getString(R.string.operation));

        if (getArguments() != null){
            mSelectedFileName = getArguments().getString("file_name", "");
        }

        mListView = (ListView) view.findViewById(android.R.id.list);
        SimpleAdapter simpleAdapter = new SimpleAdapter(getActivity(), getData(), R.layout.operatio_item, new String[]{OPERATION}, new int[]{R.id.operation_id});
        mListView.setAdapter(simpleAdapter);

        final EditText fileNameEdit = (EditText) view.findViewById(R.id.rename_et);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                /*rename*/
//                if (position == 1) {
//                    mListView.setVisibility(View.GONE);
//                    fileNameEdit.setText(mSelectedFileName);
//                    return;
//                }
                if (mOnOperationCompleteListener != null) {
                    mOnOperationCompleteListener.onOperationComplete(position, mSelectedFileName);
                }
                dismiss();
            }
        });

        Button renameConfirm = (Button) view.findViewById(R.id.confirm_action);
        renameConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = fileNameEdit.getText().toString().trim();
                if (!TextUtils.isEmpty(newName)){
                    if (mOnOperationCompleteListener != null) {
                        /*rename*/
                        mOnOperationCompleteListener.onOperationComplete(1, newName);
                    }
                    dismiss();
                } else {
                    Toast.makeText(getActivity(), "Input error!", Toast.LENGTH_LONG).show();
                }
            }
        });
        return view;
    }

    private ArrayList<Map<String, Object>> getData(){
        ArrayList<Map<String, Object>> list = new ArrayList<>();
//        String[] operations = getActivity().getResources().getStringArray(R.array.operation_list);
        String operation;
        if(mSelectedFileName.startsWith("REC")){
            operation = getString(R.string.modify_protection);
        }else{
            operation = getString(R.string.unprotect);
        }

        Map<String, Object> map = new HashMap<>();
        map.put(OPERATION, operation);
        list.add(map);

//        for (String operation : operations){
//            Map<String, Object> map = new HashMap<String, Object>();
//            map.put(OPERATION, operation);
//            list.add(map);
//        }

        return list;
    }

    public interface OnOperationCompleteListener{
        void onOperationComplete(int operation, String message);
    }

    public void setOnOperationCompleteListener(OnOperationCompleteListener listener){
        mOnOperationCompleteListener = listener;
    }
}
