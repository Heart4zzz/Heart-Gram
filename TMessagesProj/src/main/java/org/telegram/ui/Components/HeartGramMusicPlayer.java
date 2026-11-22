package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Locale;

public class HeartGramMusicPlayer extends BottomSheet {
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    private final MediaController media = MediaController.getInstance();
    private final BackupImageView cover;
    private final TextView title, artist, elapsed, duration, speed;
    private final ImageButton play, previous, next, shuffle, repeat;
    private final HeartGramWaveSeekBar seek;
    private final int surface, container, primary, onPrimary, ink, secondary, ripple;
    private MessageObject displayedTrack;
    private MessageObject seekTrack;
    private Bitmap displayedCover;
    private boolean initializedCover;
    private boolean playing;
    private boolean shuffled;
    private int repeatMode;
    private float playbackSpeed = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updatePlayback();
            seek.postDelayed(this, 300);
        }
    };

    public HeartGramMusicPlayer(Context context, Theme.ResourcesProvider provider) {
        super(context, false, provider);
        boolean dark = Theme.isCurrentThemeDark();
        surface = dark ? 0xff241c17 : 0xfffff7ed;
        container = dark ? 0xff473329 : 0xfff0ddc5;
        primary = dark ? 0xffffbc87 : 0xff805130;
        onPrimary = dark ? 0xff452810 : 0xffffffff;
        ink = dark ? 0xfff8eadc : 0xff302219;
        secondary = dark ? 0xffdebeaa : 0xff705b4d;
        ripple = dark ? 0xff6e5140 : 0xffead0b5;
        setBackgroundColor(surface);

        final LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(8), dp(24), dp(18));
        final ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                post(tick);
            }

            @Override
            protected void onDetachedFromWindow() {
                seek.removeCallbacks(tick);
                removeCallbacks(tick);
                super.onDetachedFromWindow();
            }
        };
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(panel);

        TextView eyebrow = label(context, 14, secondary);
        eyebrow.setText(getString(R.string.HeartGramNowPlaying));
        eyebrow.setGravity(Gravity.CENTER);
        eyebrow.setTypeface(AndroidUtilities.bold());
        panel.addView(eyebrow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 32));

        FrameLayout artwork = new FrameLayout(context);
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        artwork.setBackground(Theme.createRoundRectDrawable(dp(32), container));
        artwork.setClipToOutline(true);
        ImageView artworkFallback = new ImageView(context);
        artworkFallback.setImageResource(R.drawable.music_empty);
        artworkFallback.setImageTintList(ColorStateList.valueOf(primary));
        artworkFallback.setScaleType(ImageView.ScaleType.CENTER);
        artwork.addView(artworkFallback, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        cover = new BackupImageView(context);
        artwork.addView(cover, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        int width = context.getResources().getConfiguration().screenWidthDp;
        int height = context.getResources().getConfiguration().screenHeightDp;
        int coverSize = Math.max(120, Math.min(326, Math.min(width - 52, height - 270)));
        panel.addView(artwork, LayoutHelper.createLinear(coverSize, coverSize, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 16));

        title = label(context, 25, ink);
        title.setTypeface(AndroidUtilities.bold());
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        artist = label(context, 15, secondary);
        artist.setSingleLine();
        artist.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(artist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        seek = new HeartGramWaveSeekBar(context, primary, container);
        seek.setMax(1000);
        seek.setProgressTintList(ColorStateList.valueOf(primary));
        seek.setThumbTintList(ColorStateList.valueOf(primary));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(container));
        seek.setPadding(dp(10), 0, dp(10), 0);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && seekTrack != null) {
                    elapsed.setText(time((int) (seekTrack.getDuration() * progress / bar.getMax())));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seekTrack = displayedTrack;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (seekTrack != null && seekTrack == media.getPlayingMessageObject()) {
                    media.seekToProgress(seekTrack, bar.getProgress() / (float) bar.getMax());
                }
                seekTrack = null;
            }
        });
        panel.addView(seek, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 4, 0, 0));

        LinearLayout timeRow = new LinearLayout(context);
        elapsed = label(context, 12, secondary);
        duration = label(context, 12, secondary);
        duration.setGravity(Gravity.END);
        timeRow.addView(elapsed, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1));
        timeRow.addView(duration, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1));
        panel.addView(timeRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout transport = new LinearLayout(context);
        transport.setGravity(Gravity.CENTER);
        shuffle = button(context, transport, 44, 44, false, R.string.HeartGramShuffle, R.drawable.player_new_shuffle_off,
                () -> media.setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2));
        previous = button(context, transport, 48, 48, false, R.string.HeartGramPlaybackPrevious,
                android.R.drawable.ic_media_previous, media::playPreviousMessage);
        play = button(context, transport, 68, 64, true, R.string.HeartGramPlayPause, android.R.drawable.ic_media_play, () -> {
            if (displayedTrack != null) {
                if (media.isMessagePaused()) {
                    media.playMessage(displayedTrack);
                } else {
                    media.pauseMessage(displayedTrack);
                }
                updatePlayback();
            }
        });
        next = button(context, transport, 48, 48, false, R.string.HeartGramPlaybackNext,
                android.R.drawable.ic_media_next, media::playNextMessage);
        repeat = button(context, transport, 44, 44, false, R.string.HeartGramRepeat, R.drawable.player_new_repeat_off,
                () -> SharedConfig.setRepeatMode((SharedConfig.repeatMode + 1) % 3));
        panel.addView(transport, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 76, 0, 4, 0, 0));

        LinearLayout tools = new LinearLayout(context);
        tools.setGravity(Gravity.CENTER);
        speed = chip(context, tools, R.string.HeartGramPlaybackSpeed, this::showSpeeds);
        chip(context, tools, R.string.HeartGramPlaybackQueue, this::showQueue);
        panel.addView(tools, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        setCustomView(scroll);
        updatePlayback();
    }

    private TextView label(Context context, int size, int color) {
        TextView text = new TextView(context);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private ImageButton button(Context context, LinearLayout row, int width, int height, boolean main,
                               int description, int icon, Runnable click) {
        ImageButton button = new ImageButton(context);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setContentDescription(getString(description));
        button.setImageResource(icon);
        button.setImageTintList(ColorStateList.valueOf(main ? onPrimary : ink));
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(main ? 24 : 18),
                main ? primary : container, ripple));
        button.setOnClickListener(v -> {
            click.run();
            updatePlayback();
        });
        row.addView(button, LayoutHelper.createLinear(width, height, Gravity.CENTER_VERTICAL, 2, 0, 2, 0));
        return button;
    }

    private TextView chip(Context context, LinearLayout row, int text, Runnable click) {
        TextView chip = label(context, 14, ink);
        chip.setText(getString(text));
        chip.setGravity(Gravity.CENTER);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setPadding(dp(18), 0, dp(18), 0);
        chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(22), container, ripple));
        chip.setOnClickListener(v -> click.run());
        row.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
        return chip;
    }

    private void updatePlayback() {
        MessageObject track = media.getPlayingMessageObject();
        if (track != null && !track.isMusic()) {
            track = null;
        }
        boolean trackChanged = track != displayedTrack || !initializedCover;
        if (trackChanged) {
            displayedTrack = track;
            title.setText(track == null ? getString(R.string.HeartGramNoMusic) : track.getMusicTitle());
            artist.setText(track == null ? "" : track.getMusicAuthor());
            play.setEnabled(track != null);
            previous.setEnabled(track != null);
            next.setEnabled(track != null);
        }
        AudioInfo info = track == null ? null : media.getAudioInfo();
        Bitmap artwork = info == null ? null : info.getCover();
        if (trackChanged || artwork != displayedCover) {
            initializedCover = true;
            displayedCover = artwork;
            if (artwork != null) {
                cover.setImageBitmap(artwork);
            } else if (track != null && track.getDocument() != null) {
                TLRPC.Document document = track.getDocument();
                TLRPC.PhotoSize photo = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 360);
                ImageLocation thumb = ImageLocation.getForDocument(photo, document);
                cover.setImage(thumb, "360_360", (android.graphics.drawable.Drawable) null, track);
            } else {
                cover.setImageDrawable(null);
            }
        }
        boolean active = track != null && !media.isMessagePaused() && !media.isDownloadingCurrentMessage();
        if (playing != active) {
            playing = active;
            seek.setPlaying(active);
            play.setImageResource(active ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        }
        seek.setEnabled(track != null && !media.isDownloadingCurrentMessage());
        if (seekTrack == null) {
            seek.setProgress(track == null ? 0 : Math.round(track.audioProgress * seek.getMax()));
            elapsed.setText(time(track == null ? 0 : track.audioProgressSec));
        }
        duration.setText(time(track == null ? 0 : (int) track.getDuration()));
        if (shuffled != SharedConfig.shuffleMusic) {
            shuffled = SharedConfig.shuffleMusic;
            shuffle.setImageResource(shuffled ? R.drawable.player_new_shuffle : R.drawable.player_new_shuffle_off);
            shuffle.setSelected(shuffled);
        }
        int mode = SharedConfig.repeatMode;
        if (mode != repeatMode) {
            repeatMode = mode;
            repeat.setImageResource(mode == 2 ? R.drawable.player_new_repeatone : mode == 1 ?
                    R.drawable.player_new_repeatall : R.drawable.player_new_repeat_off);
            repeat.setSelected(mode != 0);
        }
        float currentSpeed = media.getPlaybackSpeed(true);
        if (playbackSpeed != currentSpeed) {
            playbackSpeed = currentSpeed;
            speed.setText(speedLabel(currentSpeed));
            speed.setContentDescription(getString(R.string.HeartGramPlaybackSpeed) + " " + speed.getText());
        }
    }

    private void showSpeeds() {
        CharSequence[] labels = new CharSequence[SPEEDS.length];
        for (int i = 0; i < SPEEDS.length; i++) {
            labels[i] = speedLabel(SPEEDS[i]);
        }
        new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramPlaybackSpeed))
                .setItems(labels, (dialog, index) -> {
                    media.setPlaybackSpeed(true, SPEEDS[index]);
                    updatePlayback();
                }).show();
    }

    private void showQueue() {
        ArrayList<MessageObject> queue = new ArrayList<>(media.getPlaylist());
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramPlaybackQueue));
        if (queue.isEmpty()) {
            dialog.setMessage(getString(R.string.HeartGramNoMusic));
        } else {
            CharSequence[] labels = new CharSequence[queue.size()];
            for (int i = 0; i < queue.size(); i++) {
                MessageObject item = queue.get(i);
                labels[i] = (item == displayedTrack ? "♫  " : "") + item.getMusicTitle() + " · " + item.getMusicAuthor();
            }
            dialog.setItems(labels, (ignored, index) -> {
                media.playMessage(queue.get(index));
                updatePlayback();
            });
        }
        dialog.setNegativeButton(getString(R.string.Close), null).show();
    }

    private static String time(int seconds) {
        return String.format(Locale.getDefault(), "%d:%02d", Math.max(seconds, 0) / 60, Math.max(seconds, 0) % 60);
    }

    private static String speedLabel(float speed) {
        return new java.text.DecimalFormat("0.##").format(speed) + "×";
    }
}
