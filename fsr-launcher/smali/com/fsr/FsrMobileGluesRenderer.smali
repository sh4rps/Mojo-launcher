.class public final Lcom/fsr/FsrMobileGluesRenderer;
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
    new-instance v1, Ljava/lang/StringBuilder;
    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V
    sget-object v2, Lcom/movtery/zalithlauncher/utils/path/PathManager;->DIR_GAME_HOME:Ljava/lang/String;
    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    const-string v2, "/mobileglues"
    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v1
    new-instance v2, Ljava/io/File;
    invoke-direct {v2, v1}, Ljava/io/File;-><init>(Ljava/lang/String;)V
    invoke-virtual {v2}, Ljava/io/File;->mkdirs()Z
    const-string v2, "MG_DIR_PATH"
    invoke-virtual {v0, v2, v1}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v1, Lcom/fsr/ConstLazy;
    invoke-direct {v1, v0}, Lcom/fsr/ConstLazy;-><init>(Ljava/lang/Object;)V
    return-object v1
.end method

.method public getRendererId()Ljava/lang/String;
    .locals 1
    const-string v0, "opengles3_mobileglues"
    return-object v0
.end method

.method public getRendererLibrary()Ljava/lang/String;
    .locals 1
    const-string v0, "libmobileglues.so"
    return-object v0
.end method

.method public getRendererName()Ljava/lang/String;
    .locals 1
    const-string v0, "MobileGlues"
    return-object v0
.end method

.method public getUniqueIdentifier()Ljava/lang/String;
    .locals 1
    const-string v0, "f5a1c3d2-7b4e-4c19-9a21-5e0d3f7a1002"
    return-object v0
.end method
