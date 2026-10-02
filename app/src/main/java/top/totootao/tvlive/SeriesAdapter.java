package top.totootao.tvlive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 左侧电视剧频道列表适配器。选中即切台（焦点到达即触发，符合电视遥控习惯）。
 */
public class SeriesAdapter extends RecyclerView.Adapter<SeriesAdapter.VH> {

    public interface OnSelectListener {
        void onSelect(int position);
    }

    private final List<String> series;
    private final OnSelectListener listener;
    private int selected = 0;

    public SeriesAdapter(List<String> series, OnSelectListener listener) {
        this.series = series;
        this.listener = listener;
    }

    public void setSelected(int pos) {
        if (pos == selected) {
            return;
        }
        this.selected = pos;
        notifyDataSetChanged();
    }

    public int getSelected() {
        return selected;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.series_item, parent, false);
        return new VH(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        boolean isSel = position == selected;
        h.tv.setText(series.get(position));
        h.tv.setSelected(isSel);
        h.itemView.setOnClickListener(v -> listener.onSelect(position));
        h.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                listener.onSelect(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return series.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView tv;

        VH(View v) {
            super(v);
            tv = (TextView) v;
        }
    }
}
