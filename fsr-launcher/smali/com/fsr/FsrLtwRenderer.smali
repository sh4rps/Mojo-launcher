.class public final Lcom/fsr/FsrLtwRenderer;
.super Ljava/lang/Object;
.implements Lcom/movtery/zalithlauncher/renderer/RendererInterface;

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public getDlopenLibrary()Lkotlin/Lazy;
    .locals 2
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    new-instance v1, Lcom/fsr/ConstLazy;
    invoke-direct {v1, v0}, Lcom/fsr/ConstLazy;-><init>(Ljava/lang/Object;)V
    return-object v1
.end method

.method public getRendererEGL()Ljava/lang/String;
    .locals 1
    const/4 v0, 0x0
    return-object v0
.end method

.method public getRendererEnv()Lkotlin/Lazy;
    .locals 4
    new-instance v0, Ljava/util/HashMap;
    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V
    const-string v1, "LIBGL_ES"
    const-string v2, "3"
    invoke-virtual {v0, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v1, Lcom/fsr/ConstLazy;
    invoke-direct {v1, v0}, Lcom/fsr/ConstLazy;-><init>(Ljava/lang/Object;)V
    return-object v1
.end method

.method public getRendererId()Ljava/lang/String;
    .locals 1
    const-string v0, "opengles3_ltw"
    return-object v0
.end method

.method public getRendererLibrary()Ljava/lang/String;
    .locals 1
    const-string v0, "libltw.so"
    return-object v0
.end method

.method public getRendererName()Ljava/lang/String;
    .locals 1
    const-string v0, "LTW (OpenLTW)"
    return-object v0
.end method

.method public getUniqueIdentifier()Ljava/lang/String;
    .locals 1
    const-string v0, "f5a1c3d2-7b4e-4c19-9a21-5e0d3f7a1001"
    return-object v0
.end method
