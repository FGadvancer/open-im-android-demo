package io.openim.android.ouicontact.ui;

import android.os.Bundle;
import android.widget.Toast;

import io.openim.android.ouicontact.databinding.ActivityFriendRequestDetailBinding;
import io.openim.android.ouicontact.vm.ContactVM;
import io.openim.android.ouicore.base.BaseActivity;
import io.openim.android.ouicore.utils.SinkHelper;

public class FriendRequestDetailActivity extends BaseActivity<ContactVM, ActivityFriendRequestDetailBinding> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        bindVMByCache(ContactVM.class);
        super.onCreate(savedInstanceState);
        bindViewDataBinding(ActivityFriendRequestDetailBinding.inflate(getLayoutInflater()));
        setLightStatus();
        SinkHelper.get(this).setTranslucentStatus(view.getRoot());
        view.setContactVM(vm);

        try {
            view.avatar.load(vm.friendDetail.val().getFromFaceURL(),vm.friendDetail.val().getFromNickname());
            view.nickName.setText(vm.friendDetail.val().getFromNickname());
            view.hil.setText(vm.friendDetail.val().getReqMsg());
        }catch (Exception ignore){}
    }

    @Override
    public void onSuccess(Object body) {
        super.onSuccess(body);
        finish();
        Toast.makeText(this, getString(io.openim.android.ouicore.R.string.send_succ), Toast.LENGTH_SHORT).show();
    }
}
