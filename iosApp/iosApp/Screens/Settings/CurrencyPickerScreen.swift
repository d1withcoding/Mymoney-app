import SwiftUI

struct CurrencyPickerScreen: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    @State private var searchText = ""
    @Environment(\.dismiss) private var dismiss

    private var filteredCurrencies: [CurrencyInfo] {
        if searchText.isEmpty { return Self.currencies }
        let query = searchText.lowercased()
        return Self.currencies.filter {
            $0.code.lowercased().contains(query) ||
            $0.name.lowercased().contains(query) ||
            $0.symbol.lowercased().contains(query)
        }
    }

    var body: some View {
        List(filteredCurrencies) { currency in
            Button {
                currencyManager.displayCurrency = currency.code
                dismiss()
            } label: {
                HStack(spacing: AppSpacing.md) {
                    Text(currency.symbol)
                        .font(.title3)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                        .frame(width: 40)

                    VStack(alignment: .leading, spacing: AppSpacing.xs) {
                        Text(currency.code)
                            .font(AppTypography.body)
                            .foregroundStyle(.primary)
                        Text(currency.name)
                            .font(AppTypography.caption)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    if currencyManager.displayCurrency == currency.code {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(ThemeManager.shared.accentColor)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .searchable(text: $searchText, prompt: "Search currencies")
        .navigationTitle("Currency")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Currency Data

struct CurrencyInfo: Identifiable {
    var id: String { code }
    let code: String
    let name: String
    let symbol: String
}

extension CurrencyPickerScreen {
    static let currencies: [CurrencyInfo] = [
        CurrencyInfo(code: "GHS", name: "Ghanaian Cedi", symbol: "GH\u{20B5}"),
    ]
}
