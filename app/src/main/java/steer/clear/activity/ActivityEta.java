package steer.clear.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import java.util.Observable;

import javax.inject.Inject;

import butterknife.ButterKnife;
import butterknife.Bind;
import butterknife.OnClick;
import retrofit.RetrofitError;
import retrofit.client.Response;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import steer.clear.MainApp;
import steer.clear.R;
import steer.clear.event.EventLogout;
import steer.clear.retrofit.Client;
import steer.clear.util.Datastore;
import steer.clear.util.LoadingDialog;
import steer.clear.util.Logg;
import steer.clear.view.ViewFooter;
import steer.clear.view.ViewTypefaceTextView;

public class ActivityEta extends ActivityBase implements View.OnClickListener {

    @Bind(R.id.activity_eta_time_prefix) ViewTypefaceTextView prefix;
    @Bind(R.id.activity_eta_time) ViewTypefaceTextView etaTime;
    @Bind(R.id.activity_eta_cancel_ride) ViewFooter cancelRide;

    private int cancelId;
    private String eta;
    private boolean saveInfo = true;

    public final static String ETA = "eta";
    public final static String CANCEL = "CANCEL_ID";

    private LoadingDialog loadingDialog;

    public static Intent newIntent(Context context, String eta, int cancelId) {
        Intent etaActivity = new Intent(context, ActivityEta.class);
        etaActivity.putExtra(ActivityEta.ETA, eta);
        etaActivity.putExtra(ActivityEta.CANCEL, cancelId);
        etaActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        etaActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return etaActivity;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eta);
        ButterKnife.bind(this);

        if (savedInstanceState != null) {
            cancelId = savedInstanceState.getInt(CANCEL);
            eta = savedInstanceState.getString(ETA);
            etaTime.setText(eta);
        } else {
            Intent extras = getIntent();
            eta = extras.getStringExtra(ETA);
            cancelId = extras.getIntExtra(CANCEL, 0);
            etaTime.setText(eta);
        }

        loadingDialog = new LoadingDialog(this, R.style.ProgressDialogTheme);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ETA, eta);
        outState.putInt(CANCEL, cancelId);
    }

    @Override
    protected void onPause() {
        if (saveInfo) { store.putRideInfo(eta, cancelId); }
        super.onPause();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        eta = savedInstanceState.getString(ETA);
        cancelId = savedInstanceState.getInt(CANCEL);
        etaTime.setText(eta);
    }

    @Override
    @OnClick(R.id.activity_eta_cancel_ride)
    public void onClick(View v) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(getResources().getString(R.string.dialog_cancel_ride_title))
            .setMessage(getResources().getString(R.string.dialog_cancel_ride_body))
            .setPositiveButton(
                getResources().getString(R.string.dialog_cancel_ride_pos_button_text),
                (dialog, which) -> {
                    cancelRide();
            }).setNegativeButton(
                getResources().getString(R.string.dialog_cancel_ride_neg_button_text),
                (dialog, which) -> {
                    dialog.dismiss();
            });

        alertDialog.show();
    }

    public void onEvent(EventLogout eventLogout) {

    }

    private void cancelRide() {
        loadingDialog.show();
        saveInfo = false;
        store.clearRideInfo();

        Subscriber<Response> rideCancelSubscriber = new Subscriber<Response>() {
            @Override
            public void onCompleted() {
                removeSubscription(this);
                loadingDialog.dismiss();
                startActivity(ActivityHome.newIntent(ActivityEta.this));
                finish();
            }

            @Override
            public void onError(Throwable e) {
                saveInfo = false;
                onCompleted();
            }

            @Override
            public void onNext(Response response) {

            }
        };

        addSubscription(helper.cancelRide(cancelId)
                .subscribeOn(Schedulers.io())
                .onExceptionResumeNext(rx.Observable.<Response>empty())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(rideCancelSubscriber));
    }
}