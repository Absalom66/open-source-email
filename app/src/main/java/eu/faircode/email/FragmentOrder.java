package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2019 by Marcel Bokhorst (M66B)
*/

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class FragmentOrder extends FragmentBase {
    private int title;
    private String clazz;

    private RecyclerView rvOrder;
    private ContentLoadingProgressBar pbWait;
    private Group grpReady;

    private AdapterOrder adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get arguments
        Bundle args = getArguments();
        title = args.getInt("title", -1);
        clazz = args.getString("class");
        Log.i("Order class=" + clazz);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setSubtitle(title);
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.fragment_order, container, false);

        // Get controls
        rvOrder = view.findViewById(R.id.rvOrder);
        pbWait = view.findViewById(R.id.pbWait);
        grpReady = view.findViewById(R.id.grpReady);

        // Wire controls
        rvOrder.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        rvOrder.setLayoutManager(llm);

        adapter = new AdapterOrder(getContext(), getViewLifecycleOwner());
        rvOrder.setAdapter(adapter);
        new ItemTouchHelper(touchHelper).attachToRecyclerView(rvOrder);

        // Initialize
        grpReady.setVisibility(View.GONE);
        pbWait.setVisibility(View.VISIBLE);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        DB db = DB.getInstance(getContext());

        if (EntityAccount.class.getName().equals(clazz))
            db.account().liveSynchronizingAccounts().observe(getViewLifecycleOwner(), new Observer<List<EntityAccount>>() {
                @Override
                public void onChanged(List<EntityAccount> accounts) {
                    if (accounts == null)
                        accounts = new ArrayList<>();

                    Log.i("Order " + clazz + "=" + accounts.size());

                    adapter.set((List<EntityOrder>) (List<?>) accounts);

                    pbWait.setVisibility(View.GONE);
                    grpReady.setVisibility(View.VISIBLE);
                }
            });
        else if (TupleFolderSort.class.getName().equals(clazz))
            db.folder().liveSort().observe(getViewLifecycleOwner(), new Observer<List<TupleFolderSort>>() {
                @Override
                public void onChanged(List<TupleFolderSort> folders) {
                    if (folders == null)
                        folders = new ArrayList<>();

                    Log.i("Order " + clazz + "=" + folders.size());

                    adapter.set((List<EntityOrder>) (List<?>) folders);

                    pbWait.setVisibility(View.GONE);
                    grpReady.setVisibility(View.VISIBLE);
                }
            });
        else
            throw new IllegalArgumentException();
    }

    @Override
    public void onPause() {
        super.onPause();

        List<EntityOrder> items = adapter.getItems();

        List<Long> order = new ArrayList<>();
        for (int i = 0; i < items.size(); i++)
            order.add(items.get(i).getSortId());

        Bundle args = new Bundle();
        args.putString("class", clazz);
        args.putLongArray("order", Helper.toLongArray(order));

        new SimpleTask<Void>() {
            @Override
            protected Void onExecute(Context context, Bundle args) {
                final String clazz = args.getString("class");
                final long[] order = args.getLongArray("order");

                final DB db = DB.getInstance(context);
                db.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < order.length; i++)
                            if (EntityAccount.class.getName().equals(clazz))
                                db.account().setAccountOrder(order[i], i);
                            else if (TupleFolderSort.class.getName().equals(clazz))
                                db.folder().setFolderOrder(order[i], i);
                            else
                                throw new IllegalArgumentException("Unknown class=" + clazz);
                    }
                });

                return null;
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);

            }
        }.execute(getContext(), getViewLifecycleOwner(), args, "order:set");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_sort, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_reset_order:
                onMenuResetOrder();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onMenuResetOrder() {
        Bundle args = new Bundle();
        args.putString("class", clazz);

        new SimpleTask<Void>() {
            @Override
            protected Void onExecute(Context context, Bundle args) {
                String clazz = args.getString("class");

                DB db = DB.getInstance(context);

                if (EntityAccount.class.getName().equals(clazz))
                    db.account().resetAccountOrder();
                else if (TupleFolderSort.class.getName().equals(clazz))
                    db.folder().resetFolderOrder();
                else
                    throw new IllegalArgumentException("Unknown class=" + clazz);

                return null;
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Helper.unexpectedError(getContext(), getViewLifecycleOwner(), ex);
            }
        }.execute(this, args, "order:reset");
    }

    private ItemTouchHelper.Callback touchHelper = new ItemTouchHelper.Callback() {
        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            int flags = 0;
            int pos = viewHolder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                if (pos - 1 >= 0)
                    flags |= ItemTouchHelper.UP;
                if (pos + 1 < rvOrder.getAdapter().getItemCount())
                    flags |= ItemTouchHelper.DOWN;
            }

            return makeMovementFlags(flags, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
            ((AdapterOrder) rvOrder.getAdapter()).onMove(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }
    };
}
