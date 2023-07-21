package io.openim.android.ouimeeting;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.alibaba.android.arouter.launcher.ARouter;
import com.alibaba.fastjson2.JSONObject;
import com.hjq.window.EasyWindow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.livekit.android.renderer.TextureViewRenderer;
import io.livekit.android.room.participant.ConnectionQuality;
import io.livekit.android.room.participant.Participant;
import io.livekit.android.room.track.VideoTrack;
import io.openim.android.ouicore.adapter.RecyclerViewAdapter;
import io.openim.android.ouicore.base.BaseActivity;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.databinding.OftenRecyclerViewBinding;
import io.openim.android.ouicore.databinding.ViewRecyclerViewBinding;
import io.openim.android.ouicore.entity.LoginCertificate;
import io.openim.android.ouicore.entity.ParticipantMeta;
import io.openim.android.ouicore.net.bage.GsonHel;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.Constant;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.OnDedrepClickListener;
import io.openim.android.ouicore.utils.Routes;
import io.openim.android.ouicore.utils.TimeUtil;
import io.openim.android.ouicore.widget.BottomPopDialog;
import io.openim.android.ouicore.widget.CommonDialog;
import io.openim.android.ouicore.widget.GridSpaceItemDecoration;
import io.openim.android.ouimeeting.databinding.ActivityMeetingHomeBinding;
import io.openim.android.ouimeeting.databinding.LayoutMeetingInfoDialogBinding;
import io.openim.android.ouimeeting.databinding.LayoutMemberDialogBinding;
import io.openim.android.ouimeeting.databinding.LayoutSettingDialogBinding;
import io.openim.android.ouimeeting.databinding.LayoutUserStatusBinding;
import io.openim.android.ouimeeting.databinding.MeetingIietmMemberBinding;
import io.openim.android.ouimeeting.databinding.MenuUserSettingBinding;
import io.openim.android.ouimeeting.databinding.ViewMeetingFloatBinding;
import io.openim.android.ouimeeting.databinding.ViewSingleTextureBinding;
import io.openim.android.ouimeeting.entity.RoomMetadata;
import io.openim.android.ouimeeting.vm.MeetingVM;
import io.openim.android.ouimeeting.widget.SingleTextureView;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.models.UserInfo;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.flow.StateFlow;

public class MeetingHomeActivity extends BaseActivity<MeetingVM, ActivityMeetingHomeBinding> implements MeetingVM.Interaction {

    private PageAdapter adapter;
    private BottomPopDialog bottomPopDialog, settingPopDialog, meetingInfoPopDialog, exitPopDialog;
    private RecyclerViewAdapter<Participant, MemberItemViewHolder> memberAdapter;
    //触发横屏 决定当前绑定的MeetingVM 是否释放
    private boolean triggerLandscape = false;
    private List<Participant> memberParticipants = new ArrayList<>();
    private Participant activeSpeaker;
    //每页显示多少Participant
    private final int pageShow = 4;
    private List<View> guideViews = new ArrayList<>();
    private EasyWindow<?> easyWindow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        bindVMByCache(MeetingVM.class);
        super.onCreate(savedInstanceState);
        bindViewDataBinding(ActivityMeetingHomeBinding.inflate(getLayoutInflater()));
        initView();

        if (vm.isInit) {
            if (vm.isLandscape)
                toast(getString(io.openim.android.ouicore.R.string.double_tap_tips));
            connectRoomSuccess(vm.callViewModel.
                getVideoTrack(vm.callViewModel.getRoom().getLocalParticipant()));
        } else init();

        bindVM();
        listener();
    }


    @Override
    protected void requestedOrientation() {
    }

    @Override
    public void onBackPressed() {
    }


    //分享屏幕
    private ActivityResultLauncher<Intent> screenCaptureIntentLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            int resultCode = result.getResultCode();
            Intent data = result.getData();
            if (resultCode != Activity.RESULT_OK || data == null) {
                return;
            }
            vm.startShareScreen(data);
            toast(getString(io.openim.android.ouicore.R.string.share_screen));
        });

    private void listener() {
        vm.allWatchedUserId.observe(this, v -> {
            for (Participant participant : memberParticipants) {
                if (v.equals(participant.getIdentity())) {
                    showPageFirst(participant);
                }
            }
        });
        view.landscape.setOnClickListener(v -> {
            triggerLandscape = true;
            vm.isInit = true;
            setRequestedOrientation(vm.isLandscape ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT :
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            vm.isLandscape = !vm.isLandscape;
        });

        View.OnClickListener clickListener = v -> {
            if (null == meetingInfoPopDialog)
                meetingInfoPopDialog = new BottomPopDialog(this, buildMeetingInfoPopDialogView());
            meetingInfoPopDialog.show();
        };
        view.topCenter.setOnClickListener(clickListener);
        view.down.setOnClickListener(clickListener);
        view.end.setOnClickListener(v -> {
            if (!vm.isSelfHostUser.getValue()) {
                exit(false);
                return;
            }
            if (null == exitPopDialog) exitPopDialog = new BottomPopDialog(this);
            exitPopDialog.show();
            exitPopDialog.getMainView().menu3.setOnClickListener(v1 -> exitPopDialog.dismiss());
            exitPopDialog.getMainView().menu1.setText(io.openim.android.ouicore.R.string.exit_meeting);
            exitPopDialog.getMainView().menu2.setText(io.openim.android.ouicore.R.string.finish_meeting);
            exitPopDialog.getMainView().menu2.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            exitPopDialog.getMainView().menu1.setOnClickListener(v1 -> {
                exit(false);
                exitPopDialog.dismiss();
            });
            exitPopDialog.getMainView().menu2.setOnClickListener(v1 -> {
                exit(true);
                exitPopDialog.dismiss();
            });
        });

        view.mic.setOnClickListener(v -> vm.callViewModel.setMicEnabled(view.mic.isChecked()));
        view.camera.setOnClickListener(v -> vm.callViewModel.setCameraEnabled(view.camera.isChecked()));
        view.shareScreen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                requestMediaProjection();
            } else {
                vm.stopShareScreen();
            }
        });
        view.member.setOnClickListener(v -> {
            if (null == vm.roomMetadata.val()) return;
            bottomPopDialog = new BottomPopDialog(this, buildPopView());

            List<Participant> participants = new ArrayList<>(memberParticipants);
            memberAdapter.setItems(participants);
            bottomPopDialog.show();
        });
        view.setting.setOnClickListener(v -> {
            if (null == settingPopDialog)
                settingPopDialog = new BottomPopDialog(this, buildSettingPopView());
            settingPopDialog.show();
        });

        vm.callViewModel.subscribe(vm.callViewModel.getRoom().getLocalParticipant().getEvents().getEvents(), (v) -> {
            boolean isMicrophoneEnabled = v.getParticipant().isMicrophoneEnabled();
            boolean isCameraEnabled = v.getParticipant().isCameraEnabled();
            view.mic.setChecked(isMicrophoneEnabled);
            view.mic.setText(isMicrophoneEnabled ?
                getString(io.openim.android.ouicore.R.string.mute) :
                getString(io.openim.android.ouicore.R.string.cancel_mute));
            view.camera.setChecked(isCameraEnabled);
            view.camera.setText(isCameraEnabled ?
                getString(io.openim.android.ouicore.R.string.close_camera) :
                getString(io.openim.android.ouicore.R.string.start_camera));
            return null;
        }, vm.callViewModel.buildScope());

        view.horn.setOnClickListener(v -> {
            boolean isHorn = getViewBooleanTag(v);
            v.setTag(!isHorn);
            vm.isReceiver.setValue(!isHorn);
        });
        vm.isReceiver.observe(this, aBoolean -> {
            vm.audioManager.setSpeakerphoneOn(!aBoolean);
            view.horn.setImageResource(aBoolean ? R.mipmap.ic_m_horn : R.mipmap.ic_m_receiver);
        });
        view.zoomOut.setOnClickListener(v -> {
            moveTaskToBack(true);
            // 传入 Activity 对象表示设置成局部的，不需要有悬浮窗权限
// 传入 Application 对象表示设置成全局的，但需要有悬浮窗权限
            ViewMeetingFloatBinding floatView =
                ViewMeetingFloatBinding.inflate(getLayoutInflater());
            if (null == easyWindow) {
                easyWindow = new EasyWindow<>(getApplication())
                    .setContentView(floatView.getRoot())
                    .setWidth(Common.dp2px(107))
                    .setHeight(Common.dp2px(160))
                    .setGravity(Gravity.RIGHT | Gravity.TOP)
                    // 设置成可拖拽的
                    .setDraggable()
                    .setOnClickListener(floatView.getRoot().getId(), (window, view) -> {
                        startActivity(new Intent(getApplication(), MeetingHomeActivity.class));
                        window.cancel();
                    });
            }
            easyWindow.show();
        });

    }


    private boolean getViewBooleanTag(View v) {
        Object tag = v.getTag();
        if (null == tag) tag = false;
        return (boolean) tag;
    }

    private void exit(Boolean isFinishMeeting) {
        CommonDialog commonDialog = new CommonDialog(this).atShow();
        commonDialog.getMainView().tips.setText(isFinishMeeting ?
            io.openim.android.ouicore.R.string.exit_meeting_tips2 :
            io.openim.android.ouicore.R.string.exit_meeting_tips);
        commonDialog.getMainView().cancel.setOnClickListener(v1 -> commonDialog.dismiss());
        commonDialog.getMainView().confirm.setOnClickListener(v1 -> {
            commonDialog.dismiss();
            if (isFinishMeeting) {
                vm.finishMeeting(vm.roomMetadata.val().roomID);
            }
            finish();
        });
    }


    private View buildMeetingInfoPopDialogView() {
        LayoutMeetingInfoDialogBinding v =
            LayoutMeetingInfoDialogBinding.inflate(getLayoutInflater());
        RoomMetadata roomMetadata = vm.roomMetadata.getValue();
        v.title.setText(roomMetadata.meetingName);
        List<String> ids = new ArrayList<>();
        ids.add(roomMetadata.hostUserID);
        OpenIMClient.getInstance().userInfoManager.getUsersInfo(new OnBase<List<UserInfo>>() {
            @Override
            public void onError(int code, String error) {
                toast(error);
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onSuccess(List<UserInfo> data) {
                if (data.isEmpty()) return;
                BigDecimal bigDecimal =
                    (BigDecimal.valueOf(roomMetadata.endTime - roomMetadata.startTime).divide(BigDecimal.valueOf(3600), 1, BigDecimal.ROUND_HALF_DOWN));
                String durationStr =
                    bigDecimal.toString() + BaseApp.inst().getString(io.openim.android.ouicore.R.string.hour);
                v.description.setText(getString(io.openim.android.ouicore.R.string.meeting_num)
                    + "：" + roomMetadata.roomID
                    + "\n" + getString(io.openim.android.ouicore.R.string.emcee) + "：" + data.get(0).getNickname()
                    + "\n" + getString(io.openim.android.ouicore.R.string.start_time) + "：" + TimeUtil.getTime(roomMetadata.createTime * 1000, TimeUtil.yearMonthDayFormat)
                    + "\t\t" + TimeUtil.getTime(roomMetadata.startTime * 1000,
                    TimeUtil.hourTimeFormat) + "\n"
                    + getString(io.openim.android.ouicore.R.string.meeting_duration) + "：" + durationStr);
            }
        }, ids);
        return v.getRoot();
    }

    //设置弹窗
    private View buildSettingPopView() {
        LayoutSettingDialogBinding v = LayoutSettingDialogBinding.inflate(getLayoutInflater());
        RoomMetadata roomMetadata = vm.roomMetadata.getValue();

        AtomicBoolean participantCanUnmuteSelf =
            new AtomicBoolean(roomMetadata.participantCanUnmuteSelf);
        AtomicBoolean participantCanEnableVideo =
            new AtomicBoolean(roomMetadata.participantCanEnableVideo);
        AtomicBoolean onlyHostInviteUser = new AtomicBoolean(roomMetadata.onlyHostInviteUser);
        AtomicBoolean onlyHostShareScreen = new AtomicBoolean(roomMetadata.onlyHostShareScreen);
        AtomicBoolean joinDisableMicrophone = new AtomicBoolean(roomMetadata.joinDisableMicrophone);

        v.allowCancelMute.setCheckedWithAnimation(participantCanUnmuteSelf.get());
        v.allowOpenCamera.setCheckedWithAnimation(participantCanEnableVideo.get());
        v.onlyHostShare.setCheckedWithAnimation(onlyHostShareScreen.get());
        v.onlyHostInvite.setCheckedWithAnimation(onlyHostInviteUser.get());
        v.joinMute.setCheckedWithAnimation(joinDisableMicrophone.get());

        v.allowCancelMute.setOnSlideButtonClickListener(isChecked -> participantCanUnmuteSelf.set(isChecked));
        v.allowOpenCamera.setOnSlideButtonClickListener(isChecked -> participantCanEnableVideo.set(isChecked));
        v.onlyHostShare.setOnSlideButtonClickListener(isChecked -> onlyHostShareScreen.set(isChecked));
        v.onlyHostInvite.setOnSlideButtonClickListener(isChecked -> onlyHostInviteUser.set(isChecked));
        v.joinMute.setOnSlideButtonClickListener(isChecked -> joinDisableMicrophone.set(isChecked));
        v.sure.setOnClickListener(new OnDedrepClickListener() {
            @Override
            public void click(View v) {
                Map<String, Object> map = new HashMap<>();
                map.put("roomID", vm.signalingCertificate.getRoomID());
                map.put("participantCanUnmuteSelf", participantCanUnmuteSelf.get());
                map.put("participantCanEnableVideo", participantCanEnableVideo.get());
                map.put("onlyHostInviteUser", onlyHostInviteUser.get());
                map.put("onlyHostShareScreen", onlyHostShareScreen.get());
                map.put("joinDisableMicrophone", joinDisableMicrophone.get());

                vm.updateMeetingInfo(map, data -> {
                    roomMetadata.participantCanUnmuteSelf = participantCanUnmuteSelf.get();
                    roomMetadata.participantCanEnableVideo = participantCanEnableVideo.get();
                    roomMetadata.onlyHostInviteUser = onlyHostInviteUser.get();
                    roomMetadata.onlyHostShareScreen = onlyHostShareScreen.get();
                    roomMetadata.joinDisableMicrophone = joinDisableMicrophone.get();

                    vm.roomMetadata.setValue(roomMetadata);
                    settingPopDialog.dismiss();
                });
            }
        });
        return v.getRoot();
    }


    //成员弹窗
    private View buildPopView() {
        LayoutMemberDialogBinding v = LayoutMemberDialogBinding.inflate(getLayoutInflater());
        v.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        v.recyclerView.setAdapter(memberAdapter = new RecyclerViewAdapter<Participant,
            MemberItemViewHolder>(MemberItemViewHolder.class) {

            @Override
            public void onBindView(@NonNull MemberItemViewHolder holder, Participant data,
                                   int position) {
                try {
                    ParticipantMeta participantMeta = GsonHel.fromJson(data.getMetadata(),
                        ParticipantMeta.class);
                    holder.view.avatar.load(participantMeta.userInfo.getFaceURL());
                    holder.view.name.setText(vm.getMetaUserName(participantMeta));

                    if (vm.isSelfHostUser.getValue()) {
                        holder.view.mic.setVisibility(View.VISIBLE);
                        holder.view.camera.setVisibility(View.VISIBLE);
                        holder.view.mic.setChecked(data.isMicrophoneEnabled());
                        holder.view.camera.setChecked(data.isCameraEnabled());
                        holder.view.mic.setOnClickListener(new OnDedrepClickListener() {
                            @Override
                            public void click(View v) {
                                vm.muteMic(data.getIdentity(), !holder.view.mic.isChecked());
                            }
                        });
                        holder.view.camera.setOnClickListener(new OnDedrepClickListener() {
                            @Override
                            public void click(View v) {
                                vm.muteCamera(data.getIdentity(), !holder.view.camera.isChecked());
                            }
                        });
                        holder.view.more.setOnClickListener(v -> {
                            showPopupWindow(v, data, participantMeta);
                        });
                    } else {
                        holder.view.mic.setVisibility(View.GONE);
                        holder.view.camera.setVisibility(View.GONE);
                        holder.view.more.setVisibility(View.GONE);
                    }
                    holder.view.angleMark.setVisibility(participantMeta.setTop ? View.VISIBLE :
                        View.GONE);
                } catch (Exception ignored) {
                }
            }

            private void showPopupWindow(View v, Participant data, ParticipantMeta meta) {
                PopupWindow popupWindow = new PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                MenuUserSettingBinding view = MenuUserSettingBinding.inflate(getLayoutInflater());
                view.setTop.setText(getText(meta.setTop ?
                    io.openim.android.ouicore.R.string.cancel_top :
                    io.openim.android.ouicore.R.string.set_top));
                view.setTop.setOnClickListener(v1 -> {
                    Map<String, Object> map = new HashMap<>();
                    List<String> ids = new ArrayList<>();
                    ids.add(data.getIdentity());
                    map.put("roomID", vm.signalingCertificate.getRoomID());
                    if (meta.setTop) map.put("reducePinedUserIDList", ids);
                    else map.put("addPinedUserIDList", ids);
                    vm.updateMeetingInfo(map, data1 -> {
                        ParticipantMeta participantMeta = GsonHel.fromJson(data.getMetadata(),
                            ParticipantMeta.class);
                        meta.setTop = participantMeta.setTop = !meta.setTop;
                        data.setMetadata$livekit_android_sdk_release(GsonHel.toJson(participantMeta));
                        memberAdapter.getItems().remove(data);
                        if (meta.setTop) {
                            memberAdapter.getItems().add(0, data);
                        } else {
                            memberAdapter.getItems().add(data);
                        }
                        memberAdapter.notifyDataSetChanged();
                        popupWindow.dismiss();
                    });
                });
                view.allSee.setOnClickListener(v1 -> {
                    Map<String, Object> map = new HashMap<>();
                    List<String> ids = new ArrayList<>();
                    ids.add(data.getIdentity());
                    map.put("roomID", vm.signalingCertificate.getRoomID());
                    map.put("addBeWatchedUserIDList", ids);
                    map.put("reduceBeWatchedUserIDList",
                        vm.roomMetadata.getValue().beWatchedUserIDList);

                    vm.updateMeetingInfo(map, data1 -> {
                        vm.roomMetadata.getValue().beWatchedUserIDList.add(0, data.getIdentity());
                        vm.roomMetadata.setValue(vm.roomMetadata.getValue());
                        popupWindow.dismiss();
                    });
                });

                //设置PopupWindow的视图内容
                popupWindow.setContentView(view.getRoot());
                //点击空白区域PopupWindow消失，这里必须先设置setBackgroundDrawable，否则点击无反应
                popupWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
                popupWindow.setOutsideTouchable(true);
                //PopupWindow在targetView下方弹出
                popupWindow.showAsDropDown(v, 0, Common.dp2px(-20));
            }
        });

        v.invite.setVisibility(vm.memberPermission.getValue() ? View.VISIBLE : View.GONE);
        v.invite.setOnClickListener(v1 -> {
            ARouter.getInstance().build(Routes.Contact.FORWARD).navigation(this,
                Constant.Event.FORWARD);
        });
        boolean isMuteAllMicrophone = vm.roomMetadata.getValue().isMuteAllMicrophone;

        v.allMute.setVisibility(vm.isSelfHostUser.getValue() ? View.VISIBLE : View.GONE);
        v.allMute.setText(isMuteAllMicrophone ?
            io.openim.android.ouicore.R.string.cancle_all_mute :
            io.openim.android.ouicore.R.string.all_mute);
        v.allMute.setTag(isMuteAllMicrophone);
        v.allMute.setOnClickListener(new OnDedrepClickListener() {
            @Override
            public void click(View v1) {
                Object isAllMute = v1.getTag();
                final boolean isAll = !(boolean) isAllMute;
                Map<String, Object> map = new HashMap<>();
                map.put("roomID", vm.signalingCertificate.getRoomID());
                map.put("isMuteAllMicrophone", isAll);

                vm.updateMeetingInfo(map, data -> {
                    ((TextView) v1).setText(isAll ?
                        io.openim.android.ouicore.R.string.cancle_all_mute :
                        io.openim.android.ouicore.R.string.all_mute);
                    v1.setTag(isAll);
                    vm.roomMetadata.getValue().isMuteAllMicrophone = isAll;

                    bottomPopDialog.dismiss();
                });
            }
        });
        return v.getRoot();
    }

    private void requestMediaProjection() {
        MediaProjectionManager mediaProjectionManager =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        screenCaptureIntentLauncher.launch(mediaProjectionManager.createScreenCaptureIntent());
    }

    private void updateGuideView(int startPos, int pageNum) {
        guideViews.clear();
        MeetingHomeActivity.this.view.guideGroup.removeAllViews();
        for (int i = 0; i < pageNum; i++) {
            View view = new View(this);
            view.setBackgroundResource(io.openim.android.ouicore.R.drawable.selector_guide_bg);
            view.setSelected(startPos == i);
            LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(Common.dp2px(6), Common.dp2px(6));
            layoutParams.setMargins(10, 0, 0, 0);
            MeetingHomeActivity.this.view.guideGroup.addView(view, layoutParams);
            guideViews.add(view);
        }
    }

    private void initView() {
        view.pager.setAdapter(adapter = new PageAdapter(getLayoutInflater(), vm));

        view.pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset,
                                       int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < guideViews.size(); i++) {
                    guideViews.get(i).setSelected(i == position);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

//        view.memberRecyclerView.setAdapter(adapter = new RecyclerViewAdapter<Participant,
//            UserStreamViewHolder>(UserStreamViewHolder.class) {
//
//            @Override
//            public void onBindView(@NonNull UserStreamViewHolder holder, Participant data,
//                                   int position) {
//                try {
//                    final Participant participant = data;
//                    ParticipantMeta participantMeta = GsonHel.fromJson(participant.getMetadata(),
//                        ParticipantMeta.class);
//                    vm.initVideoView(holder.view.textureView);
//                    bindRemoteViewRenderer(holder.view.textureView, participant);
//
//                    String name = getMetaUserName(participantMeta);
//                    String faceURL = participantMeta.userInfo.getFaceURL();
//                    bindUserStatus(holder.view, name, faceURL, participant);
//                    smallWindowSwitch(holder, participant.isCameraEnabled());
//                    AtomicReference<Boolean> isOpenCamera =
//                        new AtomicReference<>(participant.isCameraEnabled());
//                    subscribeUserStatus(holder.view, participant, isOpen -> {
//                        isOpenCamera.set(isOpen);
//                        smallWindowSwitch(holder, isOpen);
//                    });
//
//                    holder.view.getRoot().setOnClickListener(v -> {
//                        vm.selectCenterIndex = position;
//                        centerHandle(isOpenCamera.get(), participant, name, faceURL);
//                    });
//                    if (position == vm.selectCenterIndex) {
//                        centerHandle(isOpenCamera.get(), participant, name, faceURL);
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//
//            }
//
//            /**
//             * 屏幕中间直播画面数据处理
//             */
//            private void centerHandle(boolean isOpenCamera, Participant participant, String name,
//                                      String faceURL) {
//
//                centerCov(isOpenCamera);
//                bindRemoteViewRenderer(view.centerTextureView, participant);
//                bindUserStatus(view.centerUser, name, faceURL, participant);
//                subscribeUserStatus(view.centerUser, participant, isOpen -> {
//                    centerCov(isOpen);
//                });
//
//                bindUserStatus(view.centerUser2, name, faceURL, participant);
//                subscribeUserStatus(view.centerUser2, participant, null);
//            }
//
//            /**
//             * 下边小窗口状态切换
//             */
//            private void smallWindowSwitch(@NonNull UserStreamViewHolder holder, Boolean isOpen) {
//                if (isOpen) {
//                    holder.view.userStatus.setVisibility(View.GONE);
//                    holder.view.textureView.setVisibility(View.VISIBLE);
//                } else {
//                    holder.view.userStatus.setVisibility(View.VISIBLE);
//                    holder.view.textureView.setVisibility(View.GONE);
//                }
//            }
//        });
    }


    void init() {
        vm.init();
        vm.connectRoom();
    }

    private void bindVM() {
        view.setMeetingVM(vm);
        view.setCallViewModel(vm.callViewModel);
    }


    @Override
    protected void setLightStatus() {

    }


    @Override
    public void connectRoomSuccess(VideoTrack localVideoTrack) {
        view.landscape.setVisibility(View.VISIBLE);
//        removeRenderer(view.centerTextureView);
//        localVideoTrack.addRenderer(view.centerTextureView);
//        view.centerTextureView.setTag(localVideoTrack);

        vm.callViewModel.subscribe(vm.callViewModel.getAllParticipants(), (v) -> {
            if (v.isEmpty()) return null;
            memberParticipants = vm.handleParticipants(v);
            List<List<Participant>> data = new ArrayList<>();

            int pageNum = memberParticipants.size() / pageShow;
            if (pageNum == 0) {
                data.add(new ArrayList<>(memberParticipants));
            } else for (int i = 0; i < pageNum; i++) {
                List<Participant> participants =
                    new ArrayList<>(memberParticipants.subList(i * pageShow,
                        i * pageShow + pageShow));
                data.add(participants);
            }
            adapter.setList(data);
            updateGuideView(0, data.size());
            return null;
        });


        vm.callViewModel.subscribe(vm.callViewModel.getActiveSpeakersFlow(), (v) -> {
            if (v.isEmpty() || !TextUtils.isEmpty(vm.allWatchedUserId.val())) return null;
            showPageFirst(v.get(0));
            updateGuideView(0, adapter.getCount());
            return null;
        });
    }

    /**
     * 在说话用户/都看他
     *
     * @param first
     */
    private void showPageFirst(Participant first) {
        if (null != activeSpeaker
            && Objects.equals(first.getIdentity(),
            activeSpeaker.getIdentity())) return;
        adapter.getList().remove(activeSpeaker);
        activeSpeaker = first;
        adapter.getList().add(0, activeSpeaker);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == Constant.Event.FORWARD && null != data) {
            //在这里转发
            String id = data.getStringExtra(Constant.K_ID);
            String otherSideNickName = data.getStringExtra(Constant.K_NAME);
            String groupId = data.getStringExtra(Constant.K_GROUP_ID);
            vm.inviterUser(id, groupId);
        }
    }


    public static class MemberItemViewHolder extends RecyclerView.ViewHolder {
        public final MeetingIietmMemberBinding view;

        public MemberItemViewHolder(@NonNull View itemView) {
            super(MeetingIietmMemberBinding.inflate(LayoutInflater.from(itemView.getContext()),
                (ViewGroup) itemView, false).getRoot());
            view = MeetingIietmMemberBinding.bind(this.itemView);
        }
    }

    interface CameraOpenListener {
        void onOpen(Boolean isOpen);
    }

    @Override
    protected void onPause() {
        overridePendingTransition(0, 0);
        super.onPause();
        release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        release();
    }

    private void release() {
        if (isFinishing() && !triggerLandscape) {
            vm.audioManager.setSpeakerphoneOn(true);
            vm.onCleared();
            removeCacheVM();
        }
    }

    private static class PageAdapter extends PagerAdapter {
        private final LayoutInflater inflater;
        private List<Object> list = new ArrayList<>();
        private final MeetingVM vm;

        public PageAdapter(LayoutInflater inflater, MeetingVM vm) {
            this.inflater = inflater;
            this.vm = vm;
        }

        public List<Object> getList() {
            return list;
        }

        public void setList(List list) {
            this.list = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public int getItemPosition(Object object) {
            //  notifyDataSetChanged() 页面不刷新问题的方法
            return POSITION_NONE;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Object ob = list.get(position);
            View view_ = null;
            if (ob instanceof Participant) {
//                ViewSingleTextureBinding view = ViewSingleTextureBinding.inflate(inflater);
                Participant participant = (Participant) ob;
//
//                vm.initVideoView(view.textureView);
//                bindRemoteViewRenderer(view.textureView, participant);
//                subscribeUserStatus(view.userStatus, participant, isOpen -> {
//                    handleCenter(view, participant, isOpen);
//                });
//
//                view_ = view.getRoot();

                SingleTextureView singleTextureView = new SingleTextureView(container.getContext());
                singleTextureView.subscribeParticipant(vm, participant);
                view_ = singleTextureView;
            }
            if (ob instanceof List) {
                List<Participant> participants = (List<Participant>) ob;
                ViewRecyclerViewBinding view = ViewRecyclerViewBinding.inflate(inflater);
                view.recyclerView.setLayoutManager(new GridLayoutManager(container.getContext(),
                    2));
                RecyclerViewAdapter<Participant, UserStreamViewHolder> adapter;
                GridSpaceItemDecoration divItemDecoration =
                    new GridSpaceItemDecoration(Common.dp2px(1));
                view.recyclerView.addItemDecoration(divItemDecoration);
                view.recyclerView.setAdapter(adapter = new RecyclerViewAdapter<Participant,
                    UserStreamViewHolder>(UserStreamViewHolder.class) {

                    @Override
                    public void onBindView(@NonNull UserStreamViewHolder holder, Participant data
                        , int position) {
                        holder.setItemHeight(view.recyclerView.getHeight() / 2);

                        holder.view.subscribeParticipant(vm, data);
//                        vm.initVideoView(holder.view.textureView);
//                        bindRemoteViewRenderer(holder.view.textureView, data);
//                        subscribeUserStatus(holder.view.userStatus, data, isOpen -> {
//                            handleCenter(holder.view, data, isOpen);
//                        });
                    }
                });
                adapter.setItems(participants);
                view_ = view.getRoot();
            }
            if (null != view_) {
                container.addView(view_);
                return view_;
            }
            return container;
        }

        private void handleCenter(@NonNull ViewSingleTextureBinding view, Participant data,
                                  Boolean isOpen) {
            view.textureView.setVisibility(isOpen ? View.VISIBLE : View.GONE);
            view.avatar.setVisibility(isOpen ? View.GONE : View.VISIBLE);
            ParticipantMeta meta = GsonHel.fromJson(data.getMetadata(), ParticipantMeta.class);
            if (null != meta)
                view.avatar.load(meta.userInfo.getFaceURL(), vm.getMetaUserName(meta));
        }

        public static class UserStreamViewHolder extends RecyclerView.ViewHolder {
            public final SingleTextureView view;

            public UserStreamViewHolder(@NonNull View itemView) {
                super(new SingleTextureView(itemView.getContext()));
                view = (SingleTextureView) this.itemView;
            }
//            public UserStreamViewHolder(@NonNull View itemView) {
//                super(ViewSingleTextureBinding.inflate(LayoutInflater.from(itemView.getContext())
//                    , (ViewGroup) itemView, false).getRoot());
//                view = ViewSingleTextureBinding.bind(this.itemView);
//            }

            public void setItemHeight(int height) {
                View childAt = view.getChildAt(0);
                if (null != childAt) {
                    ViewGroup.LayoutParams params = childAt.getLayoutParams();
                    params.height = height;
                }
            }
        }

        private void removeRenderer(TextureViewRenderer textureView) {
            Object speakerVideoViewTag = textureView.getTag();
            if (speakerVideoViewTag instanceof VideoTrack) {
                ((VideoTrack) speakerVideoViewTag).removeRenderer(textureView);
            }
        }

        private void bindRemoteViewRenderer(TextureViewRenderer textureView, Participant data) {
            removeRenderer(textureView);
            vm.callViewModel.bindRemoteViewRenderer(textureView, data, new Continuation<Unit>() {
                @NonNull
                @Override
                public CoroutineContext getContext() {
                    return EmptyCoroutineContext.INSTANCE;
                }

                @Override
                public void resumeWith(@NonNull Object o) {

                }
            });
        }

        private void bindUserStatus(LayoutUserStatusBinding view, Participant participant) {
            view.mc.setVisibility(vm.isHostUser(participant) ? View.VISIBLE : View.GONE);
            view.mic.setImageResource(participant.isMicrophoneEnabled() ?
                io.openim.android.ouicore.R.mipmap.ic__mic_on :
                io.openim.android.ouicore.R.mipmap.ic__mic_off);
            ParticipantMeta meta = GsonHel.fromJson(participant.getMetadata(),
                ParticipantMeta.class);
            view.name.setText(vm.getMetaUserName(meta));
            bindConnectionQuality(view, participant.getConnectionQuality());
        }

        private void bindConnectionQuality(LayoutUserStatusBinding holder,
                                           ConnectionQuality quality) {
            switch (quality) {
                case EXCELLENT:
                    holder.net.setImageResource(io.openim.android.ouicore.R.mipmap.ic_net_excellent);
                    break;
                case GOOD:
                    holder.net.setImageResource(io.openim.android.ouicore.R.mipmap.ic_net_good);
                    break;
                default:
                    holder.net.setImageResource(io.openim.android.ouicore.R.mipmap.ic_net_poor);
            }
        }

        @NonNull
        private CoroutineScope bindCoroutineScope(View view) {
            if (view.getTag() instanceof CoroutineScope) {
                vm.callViewModel.scopeCancel((CoroutineScope) view.getTag());
            }
            CoroutineScope scope = vm.callViewModel.buildScope();
            view.setTag(scope);
            return scope;
        }

        private void subscribeUserStatus(LayoutUserStatusBinding holder, Participant participant,
                                         CameraOpenListener cameraOpenListener) {
            bindUserStatus(holder, participant);

            final CoroutineScope scope = bindCoroutineScope(holder.getRoot());
            vm.callViewModel.subscribe(participant.getEvents().getEvents(), (v) -> {
                bindUserStatus(holder, v.getParticipant());
                if (null != cameraOpenListener) {
                    cameraOpenListener.onOpen(v.getParticipant().isCameraEnabled());
                }
                StateFlow<ConnectionQuality> flow =
                    vm.callViewModel.getConnectionFlow(v.getParticipant());
                vm.callViewModel.subscribe(flow, (quality) -> {
                    bindConnectionQuality(holder, quality);
                    return null;
                }, scope);

                return null;
            }, scope);
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, @NonNull Object object) {
//            findViewTextureToRemoveRenderer(container);
            container.removeView((View) object);
        }

        private void findViewTextureToRemoveRenderer(ViewGroup container) {
            for (int i = 0; i < container.getChildCount(); i++) {
                View view = container.getChildAt(i);
                if (view instanceof TextureViewRenderer) {
                    removeRenderer((TextureViewRenderer) view);
                }
                if (view instanceof ViewGroup) {
                    findViewTextureToRemoveRenderer((ViewGroup) view);
                }
            }
        }
    }
}
