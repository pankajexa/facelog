package com.exargen.facelog.di

import android.content.Context
import com.exargen.facelog.ml.FaceAligner
import com.exargen.facelog.ml.FaceAngleValidator
import com.exargen.facelog.ml.FaceDetector
import com.exargen.facelog.ml.FaceEmbedder
import com.exargen.facelog.ml.GalleryMatcher
import com.exargen.facelog.ml.ImagePreprocessor
import com.exargen.facelog.ml.ImageQualityChecker
import com.exargen.facelog.ml.LivenessChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MLModule {

    @Provides
    @Singleton
    fun provideFaceDetector(
        @ApplicationContext context: Context,
    ): FaceDetector = FaceDetector(context)

    @Provides
    @Singleton
    fun provideFaceAligner(): FaceAligner = FaceAligner()

    @Provides
    @Singleton
    fun provideFaceEmbedder(
        @ApplicationContext context: Context,
    ): FaceEmbedder = FaceEmbedder(context)

    @Provides
    @Singleton
    fun provideLivenessChecker(
        @ApplicationContext context: Context,
    ): LivenessChecker = LivenessChecker(context)

    @Provides
    @Singleton
    fun provideGalleryMatcher(): GalleryMatcher = GalleryMatcher()

    @Provides
    @Singleton
    fun provideImagePreprocessor(): ImagePreprocessor = ImagePreprocessor()

    @Provides
    @Singleton
    fun provideFaceAngleValidator(): FaceAngleValidator = FaceAngleValidator()

    @Provides
    @Singleton
    fun provideImageQualityChecker(): ImageQualityChecker = ImageQualityChecker()
}
