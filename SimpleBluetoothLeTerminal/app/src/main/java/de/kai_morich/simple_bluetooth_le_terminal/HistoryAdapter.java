package de.kai_morich.simple_bluetooth_le_terminal;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Measurement measurement);
    }

    private final List<Measurement> measurements;
    private final OnItemClickListener listener;

    public HistoryAdapter(List<Measurement> measurements, OnItemClickListener listener) {
        this.measurements = measurements;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_measurement, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryAdapter.ViewHolder holder, int position) {
        Measurement m = measurements.get(position);
        holder.bind(m, listener);
    }

    @Override
    public int getItemCount() {
        return measurements.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textTitle, textData, textWarning;

        ViewHolder(View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.textTitle);
            textData = itemView.findViewById(R.id.textData);
            textWarning = itemView.findViewById(R.id.textWarning);
        }

        void bind(Measurement m, OnItemClickListener listener) {
            String title = (m.getLocation() != null && !m.getLocation().isEmpty())
                    ? m.getLocation()
                    : m.getCreatedAt();

            textTitle.setText(title);
            textData.setText(String.format("Temp: %.1f°C | TDS: %.0f ppm", m.getTemperature(), m.getSalinity()));

            // Опасное значение: >650 ppm
            if (m.getSalinity() > 650) {
                itemView.setBackgroundColor(Color.parseColor("#FFCDD2")); // красный
                textWarning.setVisibility(View.VISIBLE);
            } else {
                itemView.setBackgroundColor(Color.parseColor("#BBDEFB")); // голубой
                textWarning.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onItemClick(m));
        }
    }
}
