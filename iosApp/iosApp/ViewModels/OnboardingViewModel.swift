import Foundation

class OnboardingViewModel: ObservableObject {
    @Published var currentStep = 0
    func completeOnboarding() {
        UserDefaults.standard.set(true, forKey: "hasCompletedOnboarding")
    }
}
