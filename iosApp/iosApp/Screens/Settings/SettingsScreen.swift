import SwiftUI
import Shared

struct SettingsScreen: View {
    @ObservedObject private var appLockManager = AppLockManager.shared
    @StateObject private var exportImportManager = ExportImportManager()
    @State private var showingDocumentPicker = false
    @Environment(\.isAmoledActive) private var isAmoled
    @Environment(\.scenePhase) private var scenePhase
    @State private var aiStatus = OnDeviceAI.status

    var body: some View {
        List {
            // MARK: - Personalization

            Section("Personalization") {
                NavigationLink {
                    AppearanceScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Appearance")
                                .font(AppTypography.body)
                            Text("Theme, accent color, dark mode")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "paintbrush.fill")
                            .foregroundStyle(.orange)
                    }
                }

                // GHS is the only currency offered by this Ghana-focused build.
                NavigationLink {
                    CurrencyPickerScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Currency")
                                .font(AppTypography.body)
                            Text("Default currency for totals and new entries")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "banknote.fill")
                            .foregroundStyle(.green)
                    }
                }
            }

            // MARK: - PennyWise AI

            // Only when the phone can run it but Apple Intelligence is off or not ready;
            // an ineligible phone never hears about it.
            if case .needsSetup(let explanation) = aiStatus {
                Section("PennyWise AI") {
                    Button {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        Label {
                            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                                Text("Turn on Apple Intelligence")
                                    .font(AppTypography.body)
                                Text(explanation)
                                    .font(AppTypography.caption)
                                    .foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "sparkles")
                                .foregroundStyle(.purple)
                        }
                    }
                }
            }

            // MARK: - Data Management

            Section("Data Management") {
                NavigationLink {
                    CategoriesScreen(facade: PennyWiseSharedFacade.companion.shared)
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Categories")
                                .font(AppTypography.body)
                            Text("Manage expense and income categories")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "square.grid.2x2.fill")
                            .foregroundStyle(.purple)
                    }
                }

                NavigationLink {
                    BudgetListScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Budgets")
                                .font(AppTypography.body)
                            Text("Set spending limits by category")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "chart.pie.fill")
                            .foregroundStyle(.green)
                    }
                }

                NavigationLink {
                    AccountListScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Accounts")
                                .font(AppTypography.body)
                            Text("Manage bank accounts and cards")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "building.columns.fill")
                            .foregroundStyle(.blue)
                    }
                }

                NavigationLink {
                    SubscriptionsScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Subscriptions")
                                .font(AppTypography.body)
                            Text("Track recurring payments")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "repeat")
                            .foregroundStyle(.indigo)
                    }
                }

                NavigationLink {
                    RulesScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Smart Rules")
                                .font(AppTypography.body)
                            Text("Auto-categorize transactions")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "wand.and.stars")
                            .foregroundStyle(.pink)
                    }
                }

                NavigationLink {
                    BankSmsSetupScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Auto-add Ghana mobile money SMS")
                                .font(AppTypography.body)
                            Text("Save bank texts with a Shortcuts automation")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "message.fill")
                            .foregroundStyle(.green)
                    }
                }

                NavigationLink {
                    BankSmsSetupScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Auto-add bank SMS")
                                .font(AppTypography.body)
                            Text("Save bank texts with a Shortcuts automation")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "message.fill")
                            .foregroundStyle(.green)
                    }
                }

                Button {
                    exportImportManager.exportBackup()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Export Backup")
                                .font(AppTypography.body)
                            Text("Save your data as a JSON file")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "square.and.arrow.up")
                            .foregroundStyle(.teal)
                    }
                }
                .tint(.primary)
                .disabled(exportImportManager.isExporting)

                Button {
                    showingDocumentPicker = true
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("Import Backup")
                                .font(AppTypography.body)
                            Text("Restore from a backup file")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "square.and.arrow.down")
                            .foregroundStyle(.teal)
                    }
                }
                .tint(.primary)
                .disabled(exportImportManager.isImporting)
            }

            // MARK: - Security

            if appLockManager.canUseBiometric {
                Section("Security") {
                    Toggle(isOn: $appLockManager.appLockEnabled) {
                        Label {
                            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                                Text("App Lock")
                                    .font(AppTypography.body)
                                Text("Require \(appLockManager.biometricType) to open")
                                    .font(AppTypography.caption)
                                    .foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "lock.fill")
                                .foregroundStyle(.red)
                        }
                    }

                    if appLockManager.appLockEnabled {
                        Picker(selection: $appLockManager.lockTimeoutMinutes) {
                            Text("Immediately").tag(0)
                            Text("After 1 minute").tag(1)
                            Text("After 5 minutes").tag(5)
                            Text("After 15 minutes").tag(15)
                        } label: {
                            Label {
                                Text("Lock Timeout")
                                    .font(AppTypography.body)
                            } icon: {
                                Image(systemName: "clock.fill")
                                    .foregroundStyle(.red)
                            }
                        }
                    }
                }
            }

            // MARK: - Support

            Section("Support") {
                NavigationLink {
                    FAQScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("FAQ")
                                .font(AppTypography.body)
                            Text("Frequently asked questions")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "questionmark.circle.fill")
                            .foregroundStyle(.blue)
                    }
                }

            }

            // MARK: - About

            Section("About") {
                NavigationLink {
                    AboutScreen()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: AppSpacing.xs) {
                            Text("About PennyWise")
                                .font(AppTypography.body)
                            Text("Version, links, and credits")
                                .font(AppTypography.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "info.circle.fill")
                            .foregroundStyle(.gray)
                    }
                }
            }
        }
        .scrollContentBackground(isAmoled ? .hidden : .visible)
        .background(isAmoled ? AppColors.amoledBackground : Color.clear)
        .navigationTitle("Settings")
        .onChange(of: scenePhase) { phase in
            if phase == .active { aiStatus = OnDeviceAI.status }
        }
        .sheet(isPresented: $showingDocumentPicker) {
            DocumentPicker { url in
                exportImportManager.importBackup(from: url)
            }
        }
        .overlay {
            if let message = exportImportManager.statusMessage {
                VStack {
                    Spacer()
                    Text(message)
                        .font(AppTypography.caption)
                        .padding(AppSpacing.md)
                        .background(.regularMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.bottom, AppSpacing.lg)
                }
                .onTapGesture {
                    exportImportManager.statusMessage = nil
                }
                .onAppear {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
                        exportImportManager.statusMessage = nil
                    }
                }
            }
        }
    }
}
