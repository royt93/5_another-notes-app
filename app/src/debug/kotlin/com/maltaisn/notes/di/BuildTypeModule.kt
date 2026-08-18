package com.mckimquyen.notes.di

import com.mckimquyen.notes.ui.home.BuildTypeBehavior
import com.maltaisn.notes.ui.home.DebugBuildTypeBehavior
import dagger.Binds
import dagger.Module

@Module
abstract class BuildTypeModule {

    @Binds
    abstract fun bindBuildTypeBehavior(impl: DebugBuildTypeBehavior): BuildTypeBehavior
}
