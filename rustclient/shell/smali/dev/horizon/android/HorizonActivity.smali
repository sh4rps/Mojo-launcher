.class public final Ldev/horizon/android/HorizonActivity;
.super Landroid/app/NativeActivity;
.source "HorizonActivity.java"


# direct methods
.method static constructor <clinit>()V
    .locals 1

    .line 5
    const-string v0, "horizon_android"

    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    .line 4
    invoke-direct {p0}, Landroid/app/NativeActivity;-><init>()V

    return-void
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .locals 2

    invoke-super {p0, p1}, Landroid/app/NativeActivity;->onCreate(Landroid/os/Bundle;)V

    :try_start_0
    const/16 v0, -0x8

    invoke-static {v0}, Landroid/os/Process;->setThreadPriority(I)V

    invoke-virtual {p0}, Ldev/horizon/android/HorizonActivity;->getWindow()Landroid/view/Window;

    move-result-object v0

    const/16 v1, 0x80

    invoke-virtual {v0, v1}, Landroid/view/Window;->addFlags(I)V

    invoke-static {p0}, Lgit/artdeell/mojo/RefreshRate;->request(Landroid/app/Activity;)V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :catch_0
    return-void
.end method

.method private native nativeDocumentResult(IILjava/lang/String;)V
.end method


# virtual methods
.method protected onActivityResult(IILandroid/content/Intent;)V
    .locals 1

    .line 9
    invoke-super {p0, p1, p2, p3}, Landroid/app/NativeActivity;->onActivityResult(IILandroid/content/Intent;)V

    const/16 v0, 0x1005

    if-eq p1, v0, :cond_0

    return-void

    :cond_0
    if-eqz p3, :cond_2

    .line 12
    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object v0

    if-nez v0, :cond_1

    goto :goto_0

    :cond_1
    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object p3

    invoke-virtual {p3}, Landroid/net/Uri;->toString()Ljava/lang/String;

    move-result-object p3

    goto :goto_1

    :cond_2
    :goto_0
    const/4 p3, 0x0

    .line 11
    :goto_1
    invoke-direct {p0, p1, p2, p3}, Ldev/horizon/android/HorizonActivity;->nativeDocumentResult(IILjava/lang/String;)V

    return-void
.end method
