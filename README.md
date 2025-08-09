# DocuScan - Smart Document Scanner & Management

## 📱 Overview

DocuScan is a comprehensive Android application that transforms your smartphone into a powerful document scanning and management tool. Built with modern Android architecture and cutting-edge ML capabilities, it offers seamless document digitization, intelligent text extraction, and advanced accessibility features.

## ✨ Key Features

### 🔍 Advanced Document Scanning
- **Google ML Kit Integration**: Leverages Google's ML Kit Document Scanner for professional-quality scans
- **Multi-page Support**: Scan up to 10 pages per document with automatic detection
- **High-Quality Output**: Generates crisp, searchable PDF documents
- **Real-time Processing**: Instant document edge detection and enhancement

### 📄 Smart Document Management
- **Local Storage**: Secure document storage with Room database
- **Search Functionality**: Powerful search across all saved documents
- **Document Organization**: Automatic timestamping and metadata management
- **CRUD Operations**: Complete document lifecycle management (Create, Read, Update, Delete)

### 🖼️ Image-to-PDF Conversion
- **OCR Technology**: Intelligent text recognition using Google ML Kit Text Recognition
- **Camera Integration**: Seamless image capture with CameraX
- **PDF Generation**: Custom PDF creation with iText7 library
- **Batch Processing**: Convert multiple images into single searchable PDF
- **Visual Thumbnails**: Preview captured pages before processing

### 🗣️ Text-to-Speech Integration
- **PDF Text Extraction**: Advanced text extraction from scanned PDFs using iText7
- **Multi-language Support**: TTS support for multiple languages
- **Playback Controls**: Play, pause, resume with position memory
- **Customizable Settings**: Adjustable speech rate, pitch, and language selection
- **Smart PDF Loading**: Direct PDF selection from saved documents

### 🎨 Modern UI/UX
- **Material Design 3**: Consistent, modern interface following Google's design principles
- **Dark/Light Theme**: Adaptive theming based on system preferences
- **Intuitive Navigation**: Bottom navigation with floating action buttons
- **Onboarding Experience**: Smooth first-time user experience
- **Accessibility**: Full accessibility support with content descriptions

## 🏗️ Technical Architecture

### **Architecture Pattern**
- **MVVM (Model-View-ViewModel)**: Clean separation of concerns
- **Repository Pattern**: Centralized data access management
- **Single Activity Architecture**: Fragment-based navigation with ViewPager2

### **Core Technologies**
- **Language**: Kotlin 100%
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Build System**: Gradle with Kotlin DSL

### **Key Libraries & Frameworks**

#### **Google Services**
- **ML Kit Document Scanner** (16.0.0-beta1): Professional document scanning
- **ML Kit Text Recognition** (19.0.0): OCR capabilities
- **CameraX** (1.3.3): Modern camera implementation

#### **Database & Storage**
- **Room** (2.6.1): Local database with coroutines support
- **LiveData & ViewModel**: Reactive data observation

#### **PDF Processing**
- **iText7** (7.+): Professional PDF generation and text extraction
- **SLF4J Android** (1.7.+): Logging framework for iText

#### **UI & Graphics**
- **Material Components**: Modern Material Design 3 components
- **ViewPager2** (1.1.0): Fragment navigation
- **Glide** (4.16.0): Efficient image loading and caching
- **Lottie** (6.4.0): Smooth animations

#### **Concurrency**
- **Kotlin Coroutines** (1.7.3): Asynchronous programming
- **Lifecycle-aware Components**: Automatic lifecycle management

## 📊 Database Schema

### **ScannedDocument Entity**
```kotlin
@Entity(tableName = "scanned_documents")
data class ScannedDocument(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var title: String,
    val pdfPath: String,
    val scanTimestamp: Long = System.currentTimeMillis(),
    val pageCount: Int = 0
)
```

**Features:**
- Auto-generated unique IDs
- Editable document titles
- Automatic timestamp tracking
- PDF page count calculation
- File path management

## 🎯 Core Functionalities

### **1. Document Scanning Workflow**
1. **Initiation**: FAB button triggers Google ML Kit Document Scanner
2. **Capture**: Multi-page scanning with real-time edge detection
3. **Processing**: Automatic PDF generation with optimization
4. **Storage**: Secure local storage with database indexing
5. **Management**: Immediate availability in document library

### **2. Image-to-PDF Pipeline**
1. **Image Capture**: Camera integration with temporary file management
2. **OCR Processing**: Text extraction using ML Kit
3. **PDF Generation**: Custom PDF creation with embedded text
4. **Quality Control**: Error handling and validation
5. **File Management**: Organized storage in internal directories

### **3. Text-to-Speech Engine**
1. **PDF Selection**: Integration with saved document library
2. **Text Extraction**: Advanced parsing using iText7
3. **TTS Synthesis**: Multi-language speech generation
4. **Playback Control**: Sophisticated state management
5. **Settings Persistence**: User preference storage

### **4. Search & Organization**
- **Real-time Search**: Instant filtering across document titles
- **Chronological Sorting**: Latest documents first
- **Metadata Display**: Timestamps, page counts, file sizes
- **Batch Operations**: Multiple document management

## 🔧 Development Setup

### **Prerequisites**
- Android Studio Arctic Fox or newer
- JDK 11 or higher
- Android SDK with API 24-35
- Git for version control

### **Installation Steps**

1. **Clone Repository**
   ```bash
   git clone https://github.com/devan1shX/DocuScan.git
   cd DocuScan
   ```

2. **Open in Android Studio**
   - Import project
   - Wait for Gradle sync
   - Resolve any dependency issues

3. **Build Configuration**
   ```bash
   ./gradlew clean
   ./gradlew build
   ```

4. **Run Application**
   - Connect Android device or start emulator
   - Run configuration: `app`
   - Grant camera permissions when prompted

### **Project Structure**
```
app/src/main/java/com/example/mc/
├── MainActivity.kt                 # Main activity with ViewPager
├── SplashScreen.kt                # App launch screen
├── MainPagerAdapter.kt            # Fragment navigation
├── home_fragment/                 # Document management
│   ├── HomeFragment.kt
│   ├── adapter/DocumentAdapter.kt
│   ├── db/                        # Database layer
│   └── viewModel/MainViewModel.kt
├── imageToPdf_fragment/           # Image-to-PDF conversion
│   ├── ImageToPdfFragment.kt
│   ├── adapter/                   # Adapters for UI
│   ├── model/PdfFile.kt
│   └── viewModel/ImageToPdfViewModel.kt
├── tts_fragment/                  # Text-to-Speech
│   ├── TextToSpeechFragment.kt
│   └── viewModel/TextToSpeechViewModel.kt
└── onboarding/                    # First-time user experience
    ├── OnboardingHostActivity.kt
    ├── OnboardingManager.kt
    └── fragments/
```

## 📱 User Experience Flow

### **Onboarding Journey**
- **Welcome Screen**: Feature introduction with illustrations
- **Permission Requests**: Camera access with clear explanations
- **Quick Tour**: Essential feature highlights
- **Preference Setup**: Optional initial configuration

### **Main Application Flow**
1. **Home Tab**: Document library with search and management
2. **Scanner Tab**: Image capture and PDF generation
3. **TTS Tab**: PDF reading with voice synthesis
4. **Navigation**: Smooth transitions between features

### **Document Lifecycle**
- **Creation**: Scan → Process → Store → Index
- **Management**: View → Edit → Rename → Delete
- **Integration**: Cross-feature document sharing

## 🔒 Security & Privacy

### **Data Protection**
- **Local Storage Only**: No cloud uploads or external data sharing
- **Secure File Paths**: Protected internal storage directories
- **Permission Management**: Minimal required permissions
- **Data Isolation**: App-specific storage containers

### **File Management**
- **Automatic Cleanup**: Temporary file removal
- **Safe Deletion**: Confirmation dialogs for destructive actions
- **Backup Support**: Android Auto Backup compatibility
- **Storage Optimization**: Efficient space utilization

## 🚀 Performance Optimizations

### **Memory Management**
- **Image Loading**: Glide for efficient bitmap handling
- **Database Queries**: Optimized Room queries with indexing
- **Background Processing**: Coroutines for non-blocking operations
- **View Recycling**: Efficient RecyclerView implementations

### **Battery Efficiency**
- **Lifecycle Awareness**: Automatic resource cleanup
- **Background Limits**: Responsible background processing
- **Camera Optimization**: Efficient camera resource management

## 🧪 Testing Strategy

### **Unit Testing**
- ViewModel logic validation
- Database operations testing
- Utility function verification

### **Integration Testing**
- Fragment lifecycle testing
- Database integration validation
- File system operations

### **UI Testing**
- User interaction flows
- Navigation testing
- Accessibility validation

## 📈 Future Enhancements

### **Planned Features**
- **Cloud Sync**: Optional cloud storage integration
- **Advanced OCR**: Enhanced text recognition accuracy
- **Batch Operations**: Multiple document processing
- **Export Options**: Various format export capabilities
- **Collaboration**: Document sharing features

### **Technical Improvements**
- **Performance**: Enhanced scanning speed
- **UI/UX**: Additional customization options
- **Accessibility**: Expanded accessibility features
- **Languages**: Extended TTS language support

## 🐛 Known Issues & Limitations

### **Current Limitations**
- **TTS Languages**: Limited to system-available languages
- **File Formats**: PDF output only
- **Storage**: Internal storage only (no SD card support)
- **Batch Size**: Maximum 10 pages per scan session

### **Troubleshooting**
- **Camera Issues**: Verify permissions and restart app
- **TTS Problems**: Check system TTS settings
- **Storage Full**: Clear app cache or delete old documents
- **Performance**: Restart app if memory issues occur

## 📄 Licensing & Credits

### **Third-Party Libraries**
- **Google ML Kit**: Document scanning and text recognition
- **iText7**: PDF processing and generation
- **Room**: Android Architecture Components
- **Glide**: Image loading and caching
- **Material Components**: UI framework

### **Permissions Required**
- **Camera**: Document and image capture
- **Storage**: Local file management
- **Internet**: ML Kit model downloads (initial setup only)

## 🤝 Contributing

### **Development Guidelines**
- Follow Android development best practices
- Maintain MVVM architecture patterns
- Include comprehensive documentation
- Write unit tests for new features
- Follow Material Design guidelines

### **Code Style**
- Kotlin coding conventions
- Consistent naming patterns
- Proper documentation comments
- Resource organization

## 📞 Support & Feedback

For technical issues, feature requests, or general feedback, please create an issue in the project repository with detailed information about your device, Android version, and specific problem description.

---

**DocuScan** - Transform your mobile device into a professional document management solution with the power of machine learning and modern Android development.
