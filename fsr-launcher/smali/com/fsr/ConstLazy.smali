.class public final Lcom/fsr/ConstLazy;
.super Ljava/lang/Object;
.implements Lkotlin/Lazy;

.field private final v:Ljava/lang/Object;

.method public constructor <init>(Ljava/lang/Object;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/fsr/ConstLazy;->v:Ljava/lang/Object;
    return-void
.end method

.method public getValue()Ljava/lang/Object;
    .locals 1
    iget-object v0, p0, Lcom/fsr/ConstLazy;->v:Ljava/lang/Object;
    return-object v0
.end method

.method public isInitialized()Z
    .locals 1
    const/4 v0, 0x1
    return v0
.end method
