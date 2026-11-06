.class public final Lgit/artdeell/mojo/RefreshRate;
.super Ljava/lang/Object;
.source "RefreshRate.java"

# Mirrors the "Unlock maximum Hz" switch; refreshed every time request() runs.
.field private static sEnabled:Z = true


# Picks the display mode with the highest refresh rate that keeps the
# current physical resolution. Returns the current mode if nothing better
# exists, or null when modes can't be queried (Android < 6).
.method private static best(Landroid/view/Display;)Landroid/view/Display$Mode;
    .locals 8

    const/4 v2, 0x0

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x17

    if-ge v0, v1, :cond_sdk_ok

    return-object v2

    :cond_sdk_ok
    if-nez p0, :cond_have_display

    return-object v2

    :cond_have_display
    invoke-virtual {p0}, Landroid/view/Display;->getMode()Landroid/view/Display$Mode;

    move-result-object v0

    invoke-virtual {p0}, Landroid/view/Display;->getSupportedModes()[Landroid/view/Display$Mode;

    move-result-object v1

    if-eqz v0, :cond_ret_cur

    if-eqz v1, :cond_ret_cur

    move-object v2, v0

    const/4 v3, 0x0

    array-length v4, v1

    :cond_loop
    if-ge v3, v4, :cond_ret_best

    aget-object v5, v1, v3

    add-int/lit8 v3, v3, 0x1

    invoke-virtual {v5}, Landroid/view/Display$Mode;->getPhysicalWidth()I

    move-result v6

    invoke-virtual {v0}, Landroid/view/Display$Mode;->getPhysicalWidth()I

    move-result v7

    if-ne v6, v7, :cond_loop

    invoke-virtual {v5}, Landroid/view/Display$Mode;->getPhysicalHeight()I

    move-result v6

    invoke-virtual {v0}, Landroid/view/Display$Mode;->getPhysicalHeight()I

    move-result v7

    if-ne v6, v7, :cond_loop

    invoke-virtual {v5}, Landroid/view/Display$Mode;->getRefreshRate()F

    move-result v6

    invoke-virtual {v2}, Landroid/view/Display$Mode;->getRefreshRate()F

    move-result v7

    cmpl-float v6, v6, v7

    if-lez v6, :cond_loop

    move-object v2, v5

    goto :cond_loop

    :cond_ret_best
    return-object v2

    :cond_ret_cur
    return-object v0
.end method


# Asks the window of the given activity to use the highest refresh rate mode.
.method public static request(Landroid/app/Activity;)V
    .locals 5

    :try_start_0
    const/4 v0, 0x1

    sput-boolean v0, Lgit/artdeell/mojo/RefreshRate;->sEnabled:Z

    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;

    move-result-object v1

    invoke-virtual {v1}, Landroid/view/Window;->getAttributes()Landroid/view/WindowManager$LayoutParams;

    move-result-object v2

    if-eqz v0, :cond_off

    invoke-virtual {p0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-static {v0}, Lgit/artdeell/mojo/RefreshRate;->best(Landroid/view/Display;)Landroid/view/Display$Mode;

    move-result-object v0

    if-nez v0, :cond_have_mode

    return-void

    :cond_have_mode
    invoke-virtual {v0}, Landroid/view/Display$Mode;->getModeId()I

    move-result v3

    iput v3, v2, Landroid/view/WindowManager$LayoutParams;->preferredDisplayModeId:I

    invoke-virtual {v0}, Landroid/view/Display$Mode;->getRefreshRate()F

    move-result v3

    iput v3, v2, Landroid/view/WindowManager$LayoutParams;->preferredRefreshRate:F

    goto :cond_apply

    :cond_off
    const/4 v3, 0x0

    iput v3, v2, Landroid/view/WindowManager$LayoutParams;->preferredDisplayModeId:I

    const/4 v4, 0x0

    iput v4, v2, Landroid/view/WindowManager$LayoutParams;->preferredRefreshRate:F

    :cond_apply
    invoke-virtual {v1, v2}, Landroid/view/Window;->setAttributes(Landroid/view/WindowManager$LayoutParams;)V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    return-void
.end method


# Highest of the current rate and the best available mode's rate.
.method public static rate(Landroid/view/Display;)F
    .locals 2

    sget-boolean v0, Lgit/artdeell/mojo/RefreshRate;->sEnabled:Z

    if-nez v0, :cond_unlocked

    invoke-virtual {p0}, Landroid/view/Display;->getRefreshRate()F

    move-result v0

    return v0

    :cond_unlocked
    :try_start_1
    invoke-static {p0}, Lgit/artdeell/mojo/RefreshRate;->best(Landroid/view/Display;)Landroid/view/Display$Mode;

    move-result-object v0

    if-eqz v0, :cond_cur

    invoke-virtual {v0}, Landroid/view/Display$Mode;->getRefreshRate()F

    move-result v1

    invoke-virtual {p0}, Landroid/view/Display;->getRefreshRate()F

    move-result v0

    invoke-static {v1, v0}, Ljava/lang/Math;->max(FF)F

    move-result v0

    return v0
    :try_end_1
    .catch Ljava/lang/Throwable; {:try_start_1 .. :try_end_1} :catch_1

    :cond_cur
    invoke-virtual {p0}, Landroid/view/Display;->getRefreshRate()F

    move-result v0

    return v0

    :catch_1
    move-exception v0

    invoke-virtual {p0}, Landroid/view/Display;->getRefreshRate()F

    move-result v0

    return v0
.end method
