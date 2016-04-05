package steerclear.wm.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import butterknife.Bind;
import okhttp3.Cookie;
import okhttp3.ResponseBody;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import steerclear.wm.R;
import steerclear.wm.data.ActivitySubscriber;
import steerclear.wm.ui.view.ViewTypefaceButton;
import steerclear.wm.ui.view.ViewTypefaceEditText;
import steerclear.wm.ui.view.ViewTypefaceTextView;
import steerclear.wm.util.ErrorUtils;
import steerclear.wm.util.ViewUtils;

public class AuthenticateActivity extends BaseActivity {

    private final static String RELOGIN = "relogin";

    @Bind(R.id.activity_authenticate_logo)
    ImageView logo;
    @Bind(R.id.activity_authenticate_username)
    ViewTypefaceEditText editUsername;
    @Bind(R.id.activity_authenticate_password)
    ViewTypefaceEditText editPassword;
    @Bind(R.id.activity_authenticate_phone)
    ViewTypefaceEditText editPhone;
    @Bind(R.id.activity_authenticate_register_prompt)
    ViewTypefaceTextView prompt;
    @Bind(R.id.activity_authenticate_button)
    ViewTypefaceButton button;

    public static Intent newIntent(Context context, boolean shouldLogin) {
        Intent intent = new Intent(context, AuthenticateActivity.class);
        intent.putExtra(RELOGIN, shouldLogin);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authenticate);

        if (getIntent().getBooleanExtra(RELOGIN, false)) {
            prompt.setText(createRegisterPromptSpan());

            editUsername.setText(store.getUsername());

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean shouldRegister = editPhone.getVisibility() == View.VISIBLE;
                    if (shouldRegister) {
                        register();
                    } else {
                        login();
                    }
                }
            });
            return;
        }

        if (store.isRideInfoValid()) {
            startActivity(new Intent(this, EtaActivity.class));
            return;
        }

        if (store.hasCookie()) {
            Cookie cookie = store.getCookie();
            long expiresAt = cookie.expiresAt();
            if (expiresAt > SystemClock.elapsedRealtime()) {
                startActivity(new Intent(this, HomeActivity.class));
            }
        }
    }

    @Override
    public void onBackPressed() {
        boolean isPhoneVisible = editPhone.getVisibility() == View.VISIBLE;
        if (isPhoneVisible) {
            ViewUtils.invisible(editPhone, ViewUtils.DEFAULT_VISBILITY_DURATION).start();
            ViewUtils.visible(prompt);
            button.animateTextColorChange(Color.TRANSPARENT, button.getCurrentTextColor());
            button.setText(R.string.fragment_authenticate_login_text);
            return;
        }

        super.onBackPressed();
    }

    private boolean validateUsername(EditText editText) {
        String text = editText.getText().toString();
        boolean isLowerCase = text.matches("^[a-zA-Z0-9]*$");
        if (!isLowerCase) {
            editText.setError(getResources().getString(R.string.fragment_authenticate_username_fail));
        }
        return isLowerCase;
    }

    private boolean validatePassword(EditText editText) {
        String text = editText.getText().toString();
        boolean isEmpty = text.isEmpty();
        if (isEmpty) {
            editText.setError(getResources().getString(R.string.fragment_authenticate_password_fail));
        }
        return !isEmpty;
    }

    private boolean validatePhoneNumber(EditText editText) {
        String text = editText.getText().toString();
        boolean matches = text.matches("([0-9]{10})");
        if (!matches) {
            editText.setError(getResources().getString(R.string.fragment_authenticate_phone_fail));
        }
        return matches;
    }

    private void register() {
        boolean validateUsername = validateUsername(editUsername);
        boolean validatePassword = validatePassword(editPassword);
        boolean validatePhone = validatePhoneNumber(editPhone);
        if (validateUsername && validatePassword && validatePhone) {
            Subscriber<ResponseBody> registerSubscriber = new ActivitySubscriber<ResponseBody>(this) {
                @Override
                public void onCompleted() {
                    store.putUserHasRegistered();
                    store.putUsername(editUsername.getEnteredText());
                    login();
                }
            };

            helper.register(editUsername.getEnteredText(), editPassword.getEnteredText(), editPhone.getEnteredText())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(registerSubscriber);
        }
    }

    private void login() {
        boolean validateUsername = validateUsername(editUsername);
        boolean validatePassword = validatePassword(editPassword);
        if (validateUsername && validatePassword) {
            Subscriber<ResponseBody> loginSubscriber = new ActivitySubscriber<ResponseBody>(this) {
                @Override
                public void onCompleted() {
                    startActivity(new Intent(AuthenticateActivity.this, HomeActivity.class));
                    finish();
                }
            };

            helper.login(editUsername.getEnteredText(), editPassword.getEnteredText())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(loginSubscriber);
        }
    }

    private String formatPhoneNumber(String phoneNumber) {
        return "+1" + phoneNumber;
    }

    private SpannableString createRegisterPromptSpan() {
        prompt.setMovementMethod(LinkMovementMethod.getInstance());

        SpannableString styledString = new SpannableString(getResources().getString(R.string.fragment_authenticate_register_prompt));

        styledString.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                ViewUtils.visible(editPhone);
                ViewUtils.invisible(prompt, ViewUtils.DEFAULT_VISBILITY_DURATION).start();
                button.animateTextColorChange(Color.TRANSPARENT, button.getCurrentTextColor());
                button.setText(R.string.fragment_authenticate_register_text);
            }
        }, 23, styledString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        styledString.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.accent)),
                23, styledString.length(), 0);

        return styledString;
    }
}