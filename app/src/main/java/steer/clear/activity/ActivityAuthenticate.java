package steer.clear.activity;

import android.app.Dialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.Response;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import steer.clear.event.EventAuthenticate;
import steer.clear.util.Datastore;
import steer.clear.MainApp;
import steer.clear.R;
import steer.clear.fragment.FragmentAuthenticate;
import steer.clear.retrofit.Client;
import steer.clear.util.ErrorDialog;
import steer.clear.util.Logger;

public class ActivityAuthenticate extends AppCompatActivity {

    @Inject Client helper;
    @Inject Datastore store;
    @Inject EventBus bus;

    private static final String LOGIN_TAG = "login";
    private static final String AUTHENTICATE_TAG = "authenticate";

    public static Intent newIntent(Context context, boolean shouldLogin) {
        return new Intent(context, ActivityAuthenticate.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((MainApp) getApplication()).getApplicationComponent().inject(this);

        setContentView(R.layout.activity_authenticate);

        bus.register(this);

        addFragmentAuthenticate();
    }

    @Override
    public void onBackPressed() {
        int count = getFragmentManager().getBackStackEntryCount();
        if (count == 0) {
            super.onBackPressed();
        } else {
            getFragmentManager().popBackStack();
        }
    }

    private void addFragmentAuthenticate() {
        getFragmentManager().beginTransaction()
                .add(R.id.activity_authenticate_root,
                        FragmentAuthenticate.newInstance(store.checkRegistered()), AUTHENTICATE_TAG)
                .commit();
    }

    public void contactUs() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {"steerclear@email.wm.edu"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Steer Clear Question from the Android App");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    public void goToRegister() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.addToBackStack(LOGIN_TAG)
                .replace(R.id.activity_authenticate_root,
                        FragmentAuthenticate.newInstance(false), AUTHENTICATE_TAG)
                .commit();
    }

    public void onEvent(EventAuthenticate eventAuthenticate) {
        toggleLoadingAnimation();

        if (eventAuthenticate.registered) {
            helper.login(eventAuthenticate.username, eventAuthenticate.password)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(response -> {
                        parseResponseForCookie(response);
                        onLoginSuccess(eventAuthenticate.username);
                    }, this::onLoginError);
        } else {
            helper.register(eventAuthenticate.username, eventAuthenticate.password, eventAuthenticate.phone)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(response ->
                                    onRegisterSuccess(eventAuthenticate.username, eventAuthenticate.password),
                            this::onRegisterError);
        }
    }

    public void onRegisterSuccess(String username, String password) {
        store.userHasRegistered();
        store.putUsername(username);
        helper.login(username, password)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(response -> {
                    parseResponseForCookie(response);
                    onLoginSuccess(username);
                }, this::onLoginError);
    }

    public void onRegisterError(Throwable throwable) {
        throwable.printStackTrace();
        toggleLoadingAnimation();
        if (throwable instanceof RetrofitError) {
            RetrofitError error = (RetrofitError) throwable;
            if (error.getResponse() != null) {
                Dialog errorDialog = ErrorDialog.createFromHttpErrorCode(this, error.getResponse().getStatus());
                if (error.getResponse().getStatus() == 409) {
                    store.userHasRegistered();
                    errorDialog.setOnDismissListener(dialog -> {
                        toggleLoadingAnimation();
                        helper.login(getFragmentUsername(), getFragmentPassword())
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(response -> {
                                    onLoginSuccess(getFragmentUsername());
                                }, this::onLoginError);
                    });
                }
                errorDialog.show();
            } else {
                ErrorDialog.createFromHttpErrorCode(this, 404).show();
            }
        } else {
            ErrorDialog.createFromHttpErrorCode(this, 404).show();
        }
    }

    private void parseResponseForCookie(Response response) {
        for (Header header: response.getHeaders()) {
            if (header.getName().contains("Set-Cookie")) {
                store.putCookie(header.getValue());
                break;
            }
        }
    }

    public void onLoginSuccess(String username) {
        store.putUsername(username);
        toggleLoadingAnimation();
        startActivity(ActivityHome.newIntent(this));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    public void onLoginError(Throwable throwable) {
        throwable.printStackTrace();
        toggleLoadingAnimation();
        if (throwable instanceof RetrofitError) {
            RetrofitError error = (RetrofitError) throwable;
            ErrorDialog.createFromHttpErrorCode(this, error.getResponse() != null ?
                    error.getResponse().getStatus() : 404).show();
        } else {
            ErrorDialog.createFromHttpErrorCode(this, 404).show();
        }
    }

    private void toggleLoadingAnimation() {
        FragmentAuthenticate fragmentAuthenticate = (FragmentAuthenticate) getFragmentManager().findFragmentByTag(AUTHENTICATE_TAG);
        if (fragmentAuthenticate != null) {
            fragmentAuthenticate.togglePulse();
        }
    }

    private String getFragmentUsername() {
        FragmentAuthenticate fragmentAuthenticate = (FragmentAuthenticate) getFragmentManager().findFragmentByTag(AUTHENTICATE_TAG);
        return fragmentAuthenticate != null ? fragmentAuthenticate.getUsername() : "";
    }

    private String getFragmentPassword() {
        FragmentAuthenticate fragmentAuthenticate = (FragmentAuthenticate) getFragmentManager().findFragmentByTag(AUTHENTICATE_TAG);
        return fragmentAuthenticate != null ? fragmentAuthenticate.getPassword() : "";
    }
}
